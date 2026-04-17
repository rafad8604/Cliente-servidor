package com.app.server.dao;

import com.app.server.models.Documento;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * DAO para las tablas 'documentos' y 'documentos_chunks'.
 * IMPLEMENTACIÓN CRÍTICA: Soporta archivos >1GB mediante chunking en bloques de 50MB.
 */
public class DocumentoDAO {

    private static final int CHUNK_SIZE = 50 * 1024 * 1024; // 50MB por chunk

    private final DatabaseConnection dbPool;

    public DocumentoDAO() {
        this.dbPool = DatabaseConnection.getInstance();
    }

    /**
     * Inserta un documento con sus chunks encriptados en una transacción atómica.
     * Lee el InputStream en bloques de 50MB y hace un INSERT por cada bloque.
     *
     * @param doc    Metadatos del documento
     * @param stream InputStream de datos encriptados (puede ser >1GB)
     * @return ID del documento insertado
     */
    public long insertarConChunks(Documento doc, InputStream stream) throws SQLException, IOException {
        Connection conn = dbPool.getConnection();
        try {
            conn.setAutoCommit(false);

            // 1. Insertar metadatos del documento
            long docId = insertarDocumento(conn, doc);

            // 2. Leer stream en bloques y crear chunks
            int chunkIndex = 0;
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;

            String chunkSql = "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados) VALUES (?, ?, ?)";

            while ((bytesRead = readFully(stream, buffer)) > 0) {
                byte[] chunkData;
                if (bytesRead < buffer.length) {
                    chunkData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkData, 0, bytesRead);
                } else {
                    chunkData = buffer.clone();
                }

                try (PreparedStatement ps = conn.prepareStatement(chunkSql)) {
                    ps.setLong(1, docId);
                    ps.setInt(2, chunkIndex);
                    ps.setBytes(3, chunkData);
                    ps.executeUpdate();
                }

                chunkIndex++;
                System.out.println("[DAO] Chunk " + chunkIndex + " insertado (" + bytesRead + " bytes)");
            }

            conn.commit();
            System.out.println("[DAO] Documento ID=" + docId + " insertado con " + chunkIndex + " chunks.");
            return docId;

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            dbPool.releaseConnection(conn);
        }
    }

    /**
     * Inserta un documento de tipo MENSAJE (sin chunks de archivo grande).
     * Los datos encriptados del mensaje se almacenan como un solo chunk.
     */
    public long insertarMensaje(Documento doc, byte[] datosEncriptados) throws SQLException {
        Connection conn = dbPool.getConnection();
        try {
            conn.setAutoCommit(false);

            long docId = insertarDocumento(conn, doc);

            String chunkSql = "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(chunkSql)) {
                ps.setLong(1, docId);
                ps.setInt(2, 0);
                ps.setBytes(3, datosEncriptados);
                ps.executeUpdate();
            }

            conn.commit();
            return docId;

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            dbPool.releaseConnection(conn);
        }
    }

    /**
     * Reconstruye el stream de datos encriptados de un documento,
     * concatenando todos sus chunks ordenados.
     * Retorna un SequenceInputStream que lee los chunks bajo demanda.
     */
    public InputStream getDocumentoStream(long documentoId) throws SQLException {
        Connection conn = dbPool.getConnection();
        try {
            // Obtener cantidad de chunks
            String countSql = "SELECT COUNT(*) FROM documentos_chunks WHERE documento_id = ?";
            int totalChunks;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setLong(1, documentoId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    totalChunks = rs.getInt(1);
                }
            }

            if (totalChunks == 0) {
                dbPool.releaseConnection(conn);
                return new ByteArrayInputStream(new byte[0]);
            }

            // Crear InputStreams lazily para cada chunk
            List<InputStream> streams = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                final int chunkIdx = i;
                streams.add(new LazyChunkInputStream(documentoId, chunkIdx));
            }

            dbPool.releaseConnection(conn);

            Enumeration<InputStream> enumeration = Collections.enumeration(streams);
            return new SequenceInputStream(enumeration);

        } catch (Exception e) {
            dbPool.releaseConnection(conn);
            throw e;
        }
    }

    /**
     * Obtiene los datos encriptados de un mensaje (un solo chunk).
     */
    public byte[] getMensajeDatos(long documentoId) throws SQLException {
        String sql = "SELECT datos_encriptados FROM documentos_chunks WHERE documento_id = ? AND chunk_index = 0";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("datos_encriptados");
                }
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
        return null;
    }

    /**
     * Obtiene los metadatos de un documento por ID.
     */
    public Documento obtenerPorId(long id) throws SQLException {
        String sql = "SELECT * FROM documentos WHERE id = ?";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapFromResultSet(rs);
                }
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
        return null;
    }

    /**
     * Lista todos los documentos (solo metadatos).
     */
    public List<Documento> listarTodos() throws SQLException {
        String sql = "SELECT * FROM documentos ORDER BY fecha_creacion DESC";
        List<Documento> docs = new ArrayList<>();
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                docs.add(mapFromResultSet(rs));
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
        return docs;
    }

    /**
     * Lista documentos filtrados por tipo.
     */
    public List<Documento> listarPorTipo(Documento.Tipo tipo) throws SQLException {
        String sql = "SELECT * FROM documentos WHERE tipo = ? ORDER BY fecha_creacion DESC";
        List<Documento> docs = new ArrayList<>();
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    docs.add(mapFromResultSet(rs));
                }
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
        return docs;
    }

    // --- Métodos privados ---

    private long insertarDocumento(Connection conn, Documento doc) throws SQLException {
        String sql = "INSERT INTO documentos (nombre, extension, tamano, ruta_local_original, hash_sha256, ip_propietario, tipo) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, doc.getNombre());
            ps.setString(2, doc.getExtension());
            ps.setLong(3, doc.getTamano());
            ps.setString(4, doc.getRutaLocalOriginal());
            ps.setString(5, doc.getHashSha256());
            ps.setString(6, doc.getIpPropietario());
            ps.setString(7, doc.getTipo().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    doc.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID generado para el documento");
    }

    private Documento mapFromResultSet(ResultSet rs) throws SQLException {
        Documento doc = new Documento();
        doc.setId(rs.getLong("id"));
        doc.setNombre(rs.getString("nombre"));
        doc.setExtension(rs.getString("extension"));
        doc.setTamano(rs.getLong("tamano"));
        doc.setRutaLocalOriginal(rs.getString("ruta_local_original"));
        doc.setHashSha256(rs.getString("hash_sha256"));
        doc.setIpPropietario(rs.getString("ip_propietario"));
        doc.setTipo(Documento.Tipo.valueOf(rs.getString("tipo")));
        doc.setFechaCreacion(rs.getTimestamp("fecha_creacion").toLocalDateTime());
        return doc;
    }

    /**
     * Lee exactamente 'buffer.length' bytes del stream, o menos si llega a EOF.
     * A diferencia de InputStream.read(), esto llena el buffer completamente.
     */
    private int readFully(InputStream stream, byte[] buffer) throws IOException {
        int totalRead = 0;
        int remaining = buffer.length;
        while (remaining > 0) {
            int bytesRead = stream.read(buffer, totalRead, remaining);
            if (bytesRead == -1) break;
            totalRead += bytesRead;
            remaining -= bytesRead;
        }
        return totalRead;
    }

    /**
     * InputStream lazy que lee un chunk específico de la base de datos bajo demanda.
     * Esto evita cargar todos los chunks en memoria simultáneamente.
     */
    private class LazyChunkInputStream extends InputStream {
        private final long documentoId;
        private final int chunkIndex;
        private ByteArrayInputStream inner;
        private boolean loaded = false;

        LazyChunkInputStream(long documentoId, int chunkIndex) {
            this.documentoId = documentoId;
            this.chunkIndex = chunkIndex;
        }

        private void loadIfNeeded() throws IOException {
            if (!loaded) {
                String sql = "SELECT datos_encriptados FROM documentos_chunks WHERE documento_id = ? AND chunk_index = ?";
                Connection conn = null;
                try {
                    conn = dbPool.getConnection();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setLong(1, documentoId);
                        ps.setInt(2, chunkIndex);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                byte[] data = rs.getBytes("datos_encriptados");
                                inner = new ByteArrayInputStream(data);
                            } else {
                                inner = new ByteArrayInputStream(new byte[0]);
                            }
                        }
                    }
                } catch (SQLException e) {
                    throw new IOException("Error cargando chunk " + chunkIndex, e);
                } finally {
                    if (conn != null) dbPool.releaseConnection(conn);
                }
                loaded = true;
            }
        }

        @Override
        public int read() throws IOException {
            loadIfNeeded();
            return inner.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            loadIfNeeded();
            return inner.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            loadIfNeeded();
            return inner.available();
        }
    }
}
