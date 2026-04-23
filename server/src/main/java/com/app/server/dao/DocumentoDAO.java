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
 *
 * Soporta dos flujos:
 *  - Legado: {@link #insertarConChunks(Documento, InputStream)} inserta RAW.
 *  - Nuevo : {@link #insertarMetadatos(Documento)} + {@link #insertarChunk}
 *    para persistir chunks codificados (Base64). Usado por el paquete
 *    {@code com.app.server.storage} a través de {@code ChunkRepository}.
 */
public class DocumentoDAO {

    private static final int CHUNK_SIZE = 50 * 1024 * 1024;

    private final DatabaseConnection dbPool;
    private volatile Boolean codificacionColumnPresent;

    public DocumentoDAO() {
        this.dbPool = DatabaseConnection.getInstance();
    }

    // =========================================================================
    // API NUEVA (flujo orientado a interfaces con codificación explícita)
    // =========================================================================

    /**
     * Inserta únicamente los metadatos y devuelve el ID generado.
     * Usado por el nuevo servicio de almacenamiento basado en chunks Base64.
     */
    public long insertarMetadatos(Documento doc) throws SQLException {
        Connection conn = dbPool.getConnection();
        try {
            long id = insertarDocumento(conn, doc);
            return id;
        } finally {
            dbPool.releaseConnection(conn);
        }
    }

    /**
     * Inserta un único chunk ya codificado.
     */
    public void insertarChunk(long documentoId, int chunkIndex, byte[] datos, String codificacion)
            throws SQLException {
        Connection conn = dbPool.getConnection();
        try {
            String sql;
            boolean hasCol = hasCodificacionColumn(conn);
            if (hasCol) {
                sql = "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados, codificacion) " +
                        "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, documentoId);
                    ps.setInt(2, chunkIndex);
                    ps.setBytes(3, datos);
                    ps.setString(4, codificacion == null ? "RAW" : codificacion);
                    ps.executeUpdate();
                }
            } else {
                sql = "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados) " +
                        "VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, documentoId);
                    ps.setInt(2, chunkIndex);
                    ps.setBytes(3, datos);
                    ps.executeUpdate();
                }
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
    }

    /**
     * Lee un chunk y su codificación declarada.
     */
    public StoredChunk obtenerChunk(long documentoId, int chunkIndex) throws SQLException {
        Connection conn = dbPool.getConnection();
        try {
            boolean hasCol = hasCodificacionColumn(conn);
            String sql = hasCol
                    ? "SELECT datos_encriptados, codificacion FROM documentos_chunks WHERE documento_id = ? AND chunk_index = ?"
                    : "SELECT datos_encriptados FROM documentos_chunks WHERE documento_id = ? AND chunk_index = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, documentoId);
                ps.setInt(2, chunkIndex);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] data = rs.getBytes("datos_encriptados");
                        String cod = hasCol ? rs.getString("codificacion") : "RAW";
                        return new StoredChunk(data, cod);
                    }
                }
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
        return null;
    }

    public int contarChunks(long documentoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM documentos_chunks WHERE documento_id = ?";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
    }

    // =========================================================================
    // API LEGADA (se mantiene para compatibilidad con DocumentoService original)
    // =========================================================================

    /**
     * @deprecated Use {@code ChunkedFileStorageService} (paquete storage).
     */
    @Deprecated
    public long insertarConChunks(Documento doc, InputStream stream) throws SQLException, IOException {
        Connection conn = dbPool.getConnection();
        try {
            conn.setAutoCommit(false);
            long docId = insertarDocumento(conn, doc);

            int chunkIndex = 0;
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;

            boolean hasCol = hasCodificacionColumn(conn);
            String chunkSql = hasCol
                    ? "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados, codificacion) VALUES (?, ?, ?, ?)"
                    : "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados) VALUES (?, ?, ?)";

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
                    if (hasCol) ps.setString(4, "RAW");
                    ps.executeUpdate();
                }

                chunkIndex++;
                System.out.println("[DAO] Chunk " + chunkIndex + " insertado (" + bytesRead + " bytes)");
            }

            conn.commit();
            System.out.println("[DAO] Documento ID=" + docId + " insertado con " + chunkIndex + " chunks (legado).");
            return docId;

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            dbPool.releaseConnection(conn);
        }
    }

    public long insertarMensaje(Documento doc, byte[] datosEncriptados) throws SQLException {
        Connection conn = dbPool.getConnection();
        try {
            conn.setAutoCommit(false);
            long docId = insertarDocumento(conn, doc);

            boolean hasCol = hasCodificacionColumn(conn);
            String chunkSql = hasCol
                    ? "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados, codificacion) VALUES (?, ?, ?, ?)"
                    : "INSERT INTO documentos_chunks (documento_id, chunk_index, datos_encriptados) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(chunkSql)) {
                ps.setLong(1, docId);
                ps.setInt(2, 0);
                ps.setBytes(3, datosEncriptados);
                if (hasCol) ps.setString(4, "RAW");
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
     * Reconstruye el stream de datos encriptados de un documento. Si los
     * chunks están en BASE64, los decodifica sobre la marcha.
     */
    public InputStream getDocumentoStream(long documentoId) throws SQLException {
        int totalChunks = contarChunks(documentoId);
        if (totalChunks == 0) {
            return new ByteArrayInputStream(new byte[0]);
        }
        List<InputStream> streams = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            streams.add(new LazyChunkInputStream(documentoId, i));
        }
        Enumeration<InputStream> enumeration = Collections.enumeration(streams);
        return new SequenceInputStream(enumeration);
    }

    public byte[] getMensajeDatos(long documentoId) throws SQLException {
        StoredChunk sc = obtenerChunk(documentoId, 0);
        if (sc == null) return null;
        if ("BASE64".equalsIgnoreCase(sc.getCodificacion())) {
            return java.util.Base64.getDecoder().decode(sc.getDatos());
        }
        return sc.getDatos();
    }

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

    // =========================================================================
    // Internos
    // =========================================================================

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
     * Detecta (con cache) si la tabla tiene la columna {@code codificacion}.
     * Permite compatibilidad con BDs que aún no han sido migradas.
     */
    private boolean hasCodificacionColumn(Connection conn) {
        Boolean cached = codificacionColumnPresent;
        if (cached != null) return cached;
        synchronized (this) {
            if (codificacionColumnPresent != null) return codificacionColumnPresent;
            try (ResultSet rs = conn.getMetaData().getColumns(
                    conn.getCatalog(), null, "documentos_chunks", "codificacion")) {
                codificacionColumnPresent = rs.next();
            } catch (SQLException e) {
                codificacionColumnPresent = Boolean.FALSE;
            }
            return codificacionColumnPresent;
        }
    }

    /**
     * Chunk crudo tal como está en la BD (sin decodificar), junto con la
     * codificación declarada.
     */
    public static final class StoredChunk {
        private final byte[] datos;
        private final String codificacion;

        public StoredChunk(byte[] datos, String codificacion) {
            this.datos = datos;
            this.codificacion = codificacion == null ? "RAW" : codificacion;
        }

        public byte[] getDatos() { return datos; }
        public String getCodificacion() { return codificacion; }
    }

    /**
     * InputStream perezoso que decodifica según la codificación declarada
     * (Base64 → binario, RAW → pass-through).
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
            if (loaded) return;
            try {
                StoredChunk sc = obtenerChunk(documentoId, chunkIndex);
                byte[] data;
                if (sc == null) {
                    data = new byte[0];
                } else if ("BASE64".equalsIgnoreCase(sc.getCodificacion())) {
                    data = java.util.Base64.getDecoder().decode(sc.getDatos());
                } else {
                    data = sc.getDatos();
                }
                inner = new ByteArrayInputStream(data);
            } catch (SQLException e) {
                throw new IOException("Error cargando chunk " + chunkIndex, e);
            }
            loaded = true;
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
