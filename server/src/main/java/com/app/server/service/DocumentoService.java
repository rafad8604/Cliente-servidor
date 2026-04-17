package com.app.server.service;

import com.app.server.dao.DocumentoDAO;
import com.app.server.models.Documento;
import com.app.shared.util.CryptoUtil;

import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Servicio de negocio para documentos.
 * Coordina criptografía, almacenamiento en disco y persistencia en BD.
 */
public class DocumentoService {

    private static final String STORAGE_DIR = "./storage";

    private final DocumentoDAO documentoDAO;
    private final SecretKey serverKey;

    public DocumentoService(SecretKey serverKey) {
        this.documentoDAO = new DocumentoDAO();
        this.serverKey = serverKey;
        // Crear directorio de almacenamiento si no existe
        new File(STORAGE_DIR).mkdirs();
    }

    /**
     * Procesa un archivo recibido del cliente:
     * 1. Guarda el archivo original en disco
     * 2. Calcula hash SHA-256
     * 3. Encripta con AES-256 y almacena en chunks en la BD
     *
     * @param nombre   Nombre del archivo
     * @param tamano   Tamaño en bytes
     * @param ipOrigen IP del cliente que envía
     * @param dataIn   InputStream con los datos del archivo
     * @return El documento creado con su ID y hash
     */
    public Documento procesarArchivo(String nombre, long tamano, String ipOrigen, InputStream dataIn)
            throws IOException, GeneralSecurityException, java.sql.SQLException {

        // Determinar extensión
        String extension = "";
        int dotIdx = nombre.lastIndexOf('.');
        if (dotIdx > 0) {
            extension = nombre.substring(dotIdx + 1);
        }

        // 1. Guardar archivo original en disco
        Path archivoPath = Paths.get(STORAGE_DIR, System.currentTimeMillis() + "_" + nombre);
        long bytesRecibidos = 0;
        try (FileOutputStream fos = new FileOutputStream(archivoPath.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                bytesRecibidos += bytesRead;
            }
        }

        if (bytesRecibidos != tamano) {
            try {
                Files.deleteIfExists(archivoPath);
            } catch (IOException ignored) {
            }
            throw new IOException("Tamaño recibido incompleto. Esperado=" + tamano + " bytes, recibido=" + bytesRecibidos + " bytes");
        }

        // 2. Calcular hash SHA-256 del archivo original
        String hash = CryptoUtil.hashSHA256(archivoPath.toFile());

        // 3. Crear modelo del documento
        Documento doc = new Documento(nombre, extension, bytesRecibidos,
                archivoPath.toAbsolutePath().toString(), hash, ipOrigen, Documento.Tipo.ARCHIVO);

        // 4. Encriptar y almacenar en chunks
        byte[] iv = CryptoUtil.generateIV();
        try (FileInputStream fis = new FileInputStream(archivoPath.toFile());
             CipherInputStream cis = CryptoUtil.encryptStream(fis, serverKey, iv)) {

            // Prepend IV a los datos encriptados (primer chunk contendrá IV + datos)
            InputStream ivPrefixed = new SequenceInputStream(
                    new ByteArrayInputStream(iv), cis);

            long docId = documentoDAO.insertarConChunks(doc, ivPrefixed);
            doc.setId(docId);
        }

        return doc;
    }

    /**
     * Procesa un mensaje de texto:
     * 1. Calcula hash SHA-256
     * 2. Encripta con AES-256
     * 3. Almacena en BD como un solo chunk
     */
    public Documento procesarMensaje(String texto, String ipOrigen)
            throws GeneralSecurityException, java.sql.SQLException {

        byte[] textBytes = texto.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = CryptoUtil.hashSHA256(texto);

        Documento doc = new Documento("mensaje_" + System.currentTimeMillis(), "txt",
                textBytes.length, null, hash, ipOrigen, Documento.Tipo.MENSAJE);

        // Encriptar el mensaje
        byte[] iv = CryptoUtil.generateIV();
        byte[] encriptado = CryptoUtil.encrypt(textBytes, serverKey, iv);

        // Prepend IV al mensaje encriptado
        byte[] ivPlusData = new byte[iv.length + encriptado.length];
        System.arraycopy(iv, 0, ivPlusData, 0, iv.length);
        System.arraycopy(encriptado, 0, ivPlusData, iv.length, encriptado.length);

        long docId = documentoDAO.insertarMensaje(doc, ivPlusData);
        doc.setId(docId);

        return doc;
    }

    /**
     * Obtiene el stream de datos originales (desencriptados) de un documento.
     */
    public InputStream getArchivoOriginalStream(long documentoId) throws Exception {
        Documento doc = documentoDAO.obtenerPorId(documentoId);
        if (doc == null) throw new FileNotFoundException("Documento no encontrado: " + documentoId);

        // Si el archivo original existe en disco, devolver directamente
        if (doc.getRutaLocalOriginal() != null) {
            File archivo = new File(doc.getRutaLocalOriginal());
            if (archivo.exists()) {
                return new FileInputStream(archivo);
            }
        }

        // Si no, reconstruir desde los chunks encriptados en BD
        InputStream encriptadoStream = documentoDAO.getDocumentoStream(documentoId);

        // Leer IV (primeros 16 bytes)
        byte[] iv = new byte[16];
        int read = encriptadoStream.read(iv);
        if (read != 16) throw new IOException("No se pudo leer el IV de los datos encriptados");

        // Crear pipe para desencriptar
        PipedInputStream pipedIn = new PipedInputStream(8192);
        PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);

        // Desencriptar en un hilo separado
        Thread decryptThread = new Thread(() -> {
            try (var cos = CryptoUtil.decryptStream(pipedOut, serverKey, iv)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = encriptadoStream.read(buffer)) != -1) {
                    cos.write(buffer, 0, bytesRead);
                }
            } catch (Exception e) {
                System.err.println("[DOC] Error desencriptando: " + e.getMessage());
            }
        }, "decrypt-thread-" + documentoId);
        decryptThread.setDaemon(true);
        decryptThread.start();

        return pipedIn;
    }

    /**
     * Obtiene el stream encriptado de un documento (directo de BD).
     */
    public InputStream getArchivoEncriptadoStream(long documentoId) throws Exception {
        return documentoDAO.getDocumentoStream(documentoId);
    }

    /**
     * Obtiene el hash SHA-256 de un documento.
     */
    public String getHash(long documentoId) throws java.sql.SQLException {
        Documento doc = documentoDAO.obtenerPorId(documentoId);
        return doc != null ? doc.getHashSha256() : null;
    }

    /**
     * Obtiene el texto desencriptado de un mensaje.
     */
    public String getMensajeTexto(long documentoId) throws Exception {
        byte[] ivPlusData = documentoDAO.getMensajeDatos(documentoId);
        if (ivPlusData == null) return null;

        // Separar IV y datos
        byte[] iv = new byte[16];
        byte[] encriptado = new byte[ivPlusData.length - 16];
        System.arraycopy(ivPlusData, 0, iv, 0, 16);
        System.arraycopy(ivPlusData, 16, encriptado, 0, encriptado.length);

        byte[] original = CryptoUtil.decrypt(encriptado, serverKey, iv);
        return new String(original, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Lista todos los documentos.
     */
    public List<Documento> listarDocumentos() throws java.sql.SQLException {
        return documentoDAO.listarTodos();
    }

    /**
     * Obtiene un documento por ID.
     */
    public Documento obtenerDocumento(long id) throws java.sql.SQLException {
        return documentoDAO.obtenerPorId(id);
    }
}
