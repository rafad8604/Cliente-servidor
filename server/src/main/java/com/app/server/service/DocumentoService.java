package com.app.server.service;

import com.app.server.dao.DocumentoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.models.Documento;
import com.app.shared.util.CryptoUtil;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Servicio de negocio para documentos.
 *
 * Persistencia basada en {@link DocumentoDAO} y cifrado con {@link CryptoUtil}.
 */
public class DocumentoService {

    private static final String STORAGE_DIR = "./storage";

    private final DocumentoDAO documentoDAO;
    private final SecretKey serverKey;

    /**
     * Constructor de conveniencia: cablea las implementaciones por defecto.
     */
    public DocumentoService(SecretKey serverKey) {
        this(serverKey, null);
    }

    /**
     * Constructor con EventBus para publicar eventos del flujo.
     */
    public DocumentoService(SecretKey serverKey, ServerEventBus eventBus) {
        this.documentoDAO = new DocumentoDAO();
        this.serverKey = serverKey;
        new File(STORAGE_DIR).mkdirs();
    }

    /**
     * Flujo de archivo:
     *   1. stream -> disco local (original)
     *   2. hash SHA-256 del original
     *   3. encriptar a archivo temporal (IV + cipher)
     *   4. persistir en chunks en BD
     */
    public Documento procesarArchivo(String nombre, long tamano, String ipOrigen, InputStream dataIn)
            throws IOException, GeneralSecurityException, java.sql.SQLException {
        File storageDir = new File(STORAGE_DIR);
        storageDir.mkdirs();

        String safeName = sanitizeFileName(nombre);
        File originalFile = new File(storageDir, System.currentTimeMillis() + "_" + safeName);

        // Guardar stream entrante en disco para permitir descarga original posterior.
        try (FileOutputStream fos = new FileOutputStream(originalFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        long actualSize = originalFile.length();
        String hash = CryptoUtil.hashSHA256(originalFile);

        File encryptedTmp = new File(storageDir, originalFile.getName() + ".enc.tmp");
        CryptoUtil.encryptFile(originalFile, encryptedTmp, serverKey);

        Documento doc = new Documento(
                safeName,
                extensionOf(safeName),
                actualSize,
                originalFile.getAbsolutePath(),
                hash,
                ipOrigen,
                Documento.Tipo.ARCHIVO
        );

        try (FileInputStream fis = new FileInputStream(encryptedTmp)) {
            long docId = documentoDAO.insertarConChunks(doc, fis);
            doc.setId(docId);
        } finally {
            if (encryptedTmp.exists()) {
                encryptedTmp.delete();
            }
        }

        return doc;
    }

    /**
     * Flujo de mensajes: chunk único (no requiere Base64 ni temp local).
     */
    public Documento procesarMensaje(String texto, String ipOrigen)
            throws GeneralSecurityException, java.sql.SQLException {

        byte[] textBytes = texto.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = CryptoUtil.hashSHA256(texto);

        Documento doc = new Documento("mensaje_" + System.currentTimeMillis(), "txt",
                textBytes.length, null, hash, ipOrigen, Documento.Tipo.MENSAJE);

        byte[] iv = CryptoUtil.generateIV();
        byte[] encriptado = CryptoUtil.encrypt(textBytes, serverKey, iv);

        byte[] ivPlusData = new byte[iv.length + encriptado.length];
        System.arraycopy(iv, 0, ivPlusData, 0, iv.length);
        System.arraycopy(encriptado, 0, ivPlusData, iv.length, encriptado.length);

        long docId = documentoDAO.insertarMensaje(doc, ivPlusData);
        doc.setId(docId);

        return doc;
    }

    /**
     * Stream original (desencriptado) de un documento. Si el archivo sigue en
     * disco se devuelve directo; si no, se reconstruye desde la BD.
     */
    public InputStream getArchivoOriginalStream(long documentoId) throws Exception {
        Documento doc = documentoDAO.obtenerPorId(documentoId);
        if (doc == null) throw new FileNotFoundException("Documento no encontrado: " + documentoId);

        if (doc.getRutaLocalOriginal() != null) {
            File archivo = new File(doc.getRutaLocalOriginal());
            if (archivo.exists()) {
                return new FileInputStream(archivo);
            }
        }

        // Si el original no existe localmente, reconstruir desde chunks encriptados.
        InputStream encriptadoStream = documentoDAO.getDocumentoStream(documentoId);

        byte[] iv = new byte[16];
        int read = encriptadoStream.read(iv);
        if (read != 16) throw new IOException("No se pudo leer el IV de los datos encriptados");

        PipedInputStream pipedIn = new PipedInputStream(8192);
        PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);

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
     * Stream encriptado (sin decodificar) tal como se puede enviar a un cliente.
     */
    public InputStream getArchivoEncriptadoStream(long documentoId) throws Exception {
        return documentoDAO.getDocumentoStream(documentoId);
    }

    /**
     * Reconstruye el archivo completo (desencriptado) en memoria.
     * Apto para archivos en el orden de MB.
     */
    public byte[] reconstruirEnMemoria(long documentoId) throws Exception {
        try (InputStream in = getArchivoOriginalStream(documentoId)) {
            return in.readAllBytes();
        }
    }

    public String getHash(long documentoId) throws java.sql.SQLException {
        Documento doc = documentoDAO.obtenerPorId(documentoId);
        return doc != null ? doc.getHashSha256() : null;
    }

    public String getMensajeTexto(long documentoId) throws Exception {
        byte[] ivPlusData = documentoDAO.getMensajeDatos(documentoId);
        if (ivPlusData == null) return null;

        byte[] iv = new byte[16];
        byte[] encriptado = new byte[ivPlusData.length - 16];
        System.arraycopy(ivPlusData, 0, iv, 0, 16);
        System.arraycopy(ivPlusData, 16, encriptado, 0, encriptado.length);

        byte[] original = CryptoUtil.decrypt(encriptado, serverKey, iv);
        return new String(original, java.nio.charset.StandardCharsets.UTF_8);
    }

    public List<Documento> listarDocumentos() throws java.sql.SQLException {
        return documentoDAO.listarTodos();
    }

    public Documento obtenerDocumento(long id) throws java.sql.SQLException {
        return documentoDAO.obtenerPorId(id);
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "archivo_" + System.currentTimeMillis();
        }
        return new File(name).getName();
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return "";
        return fileName.substring(idx + 1).toLowerCase();
    }
}
