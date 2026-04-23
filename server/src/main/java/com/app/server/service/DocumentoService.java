package com.app.server.service;

import com.app.server.dao.DocumentoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.models.Documento;
import com.app.server.storage.Base64ChunkEncoder;
import com.app.server.storage.ChunkRepository;
import com.app.server.storage.ChunkedFileStorageService;
import com.app.server.storage.DiskTempStorage;
import com.app.server.storage.FixedSizeChunkSplitter;
import com.app.server.storage.InMemoryDocumentReconstructor;
import com.app.server.storage.JdbcChunkRepository;
import com.app.shared.util.CryptoUtil;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Servicio de negocio para documentos.
 *
 * Se apoya en colaboradores inyectados a través de interfaces (inversión de
 * dependencias):
 *   - {@link ChunkedFileStorageService} orquesta el flujo de archivos.
 *   - {@link InMemoryDocumentReconstructor} reconstruye en memoria.
 *   - {@link DocumentoDAO} (legado) para mensajes y consulta de metadatos.
 */
public class DocumentoService {

    private static final String STORAGE_DIR = "./storage";

    private final DocumentoDAO documentoDAO;
    private final SecretKey serverKey;
    private final ChunkedFileStorageService fileStorage;
    private final InMemoryDocumentReconstructor reconstructor;

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

        ChunkRepository repository = new JdbcChunkRepository(documentoDAO);
        Base64ChunkEncoder encoder = new Base64ChunkEncoder();

        this.fileStorage = new ChunkedFileStorageService(
                new DiskTempStorage(STORAGE_DIR),
                new FixedSizeChunkSplitter(),
                encoder,
                repository,
                eventBus,
                serverKey);

        this.reconstructor = new InMemoryDocumentReconstructor(repository, encoder, eventBus);
    }

    /**
     * Nuevo flujo:
     *   1. stream → disco local
     *   2. hash SHA-256
     *   3. encriptar → chunks 1MB → Base64 → BD
     *   4. guardar metadatos
     */
    public Documento procesarArchivo(String nombre, long tamano, String ipOrigen, InputStream dataIn)
            throws IOException, GeneralSecurityException, java.sql.SQLException {
        return fileStorage.procesarArchivo(nombre, tamano, ipOrigen, dataIn);
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

        // Reconstruir encriptado desde chunks (el reconstructor decodifica Base64)
        InputStream encriptadoStream = reconstructor.openStream(documentoId);

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
     * Internamente decodifica Base64 → bytes encriptados.
     */
    public InputStream getArchivoEncriptadoStream(long documentoId) throws Exception {
        return reconstructor.openStream(documentoId);
    }

    /**
     * Reconstruye el archivo completo (desencriptado) en memoria.
     * Apto para archivos en el orden de MB.
     */
    public byte[] reconstruirEnMemoria(long documentoId) throws Exception {
        byte[] encriptadoConIv = reconstructor.rebuildInMemory(documentoId);
        if (encriptadoConIv.length < 16) {
            throw new IOException("Documento sin IV");
        }
        byte[] iv = new byte[16];
        System.arraycopy(encriptadoConIv, 0, iv, 0, 16);
        byte[] cipher = new byte[encriptadoConIv.length - 16];
        System.arraycopy(encriptadoConIv, 16, cipher, 0, cipher.length);
        return CryptoUtil.decrypt(cipher, serverKey, iv);
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
}
