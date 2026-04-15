package com.arquitectura.servidor.business.document;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.CipherOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentReceptionService {

    private final DocumentProcessingConfig config;
    private final DocumentSecurityService securityService;
    private final DocumentRecordRepository repository;
    private final DocumentExchangeLogService exchangeLogService;
    private final ServerActivitySource activitySource;
    private final ObjectMapper objectMapper;

    public DocumentReceptionService(DocumentProcessingConfig config,
                                    DocumentSecurityService securityService,
                                    DocumentRecordRepository repository,
                                    DocumentExchangeLogService exchangeLogService,
                                    ServerActivitySource activitySource,
                                    ObjectMapper objectMapper) {
        this.config = config;
        this.securityService = securityService;
        this.repository = repository;
        this.exchangeLogService = exchangeLogService;
        this.activitySource = activitySource;
        this.objectMapper = objectMapper;
    }

    public StoredDocument receiveMessage(String senderId, String recipientId, String message) {
        return receiveMessage(senderId, recipientId, "127.0.0.1", message);
    }

    public StoredDocument receiveMessage(String senderId, String recipientId, String senderIp, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message no puede ser vacio");
        }

        String generatedName = "message-" + System.currentTimeMillis() + ".txt";
        InputStream payload = new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8));
        return receiveDocument(senderId, recipientId, normalizeIp(senderIp), DocumentType.MESSAGE, generatedName, payload);
    }

    public StoredDocument receiveFile(String senderId,
                                      String recipientId,
                                      String senderIp,
                                      String originalFileName,
                                      InputStream payload) {
        return receiveDocument(senderId, recipientId, senderIp, DocumentType.FILE, originalFileName, payload);
    }

    public StoredDocument receiveFromJson(String json, InputStream filePayload) {
        DocumentTransferCommand command;
        try {
            command = objectMapper.readValue(json, DocumentTransferCommand.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON de transferencia invalido", e);
        }

        DocumentType type = toDocumentType(command.type());
        if (type == DocumentType.MESSAGE) {
            return receiveMessage(command.senderId(), command.recipientId(), normalizeIp(command.senderIp()), command.message());
        }
        if (filePayload == null) {
            throw new IllegalArgumentException("El payload del archivo es obligatorio cuando type=FILE");
        }
        return receiveFile(command.senderId(), command.recipientId(), normalizeIp(command.senderIp()), command.fileName(), filePayload);
    }

    private StoredDocument receiveDocument(String senderId,
                                           String recipientId,
                                           String senderIp,
                                           DocumentType documentType,
                                           String originalFileName,
                                           InputStream payload) {
        validateIdentity(senderId, "senderId");
        validateIdentity(recipientId, "recipientId");
        if (payload == null) {
            throw new IllegalArgumentException("payload no puede ser nulo");
        }

        String safeFileName = sanitizeFileName(originalFileName);
        String documentId = UUID.randomUUID().toString();
        DocumentOwnerScope ownerScope = resolveScope(senderIp);
        Path originalPath = config.getOriginalDirectoryPath().resolve(documentId + "_" + safeFileName);
        Path encryptedTempPath;

        try {
            encryptedTempPath = Files.createTempFile("enc-" + documentId + "-", ".bin");
        } catch (IOException e) {
            throw new DocumentStorageException("No se pudo crear archivo temporal para cifrado", e);
        }

        MessageDigest digest = securityService.newSha256Digest();
        byte[] iv = securityService.generateInitializationVector();
        long originalSize = 0L;

        try (InputStream input = new BufferedInputStream(payload, config.getBufferSizeBytes());
             OutputStream originalOutput = new BufferedOutputStream(Files.newOutputStream(originalPath), config.getBufferSizeBytes());
             CipherOutputStream encryptedOutput = new CipherOutputStream(
                 new BufferedOutputStream(Files.newOutputStream(encryptedTempPath), config.getBufferSizeBytes()),
                 securityService.newEncryptCipher(iv)
             )) {

            byte[] buffer = new byte[config.getBufferSizeBytes()];
            int read;
            while ((read = input.read(buffer)) != -1) {
                originalOutput.write(buffer, 0, read);
                encryptedOutput.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                originalSize += read;
            }
        } catch (IOException e) {
            deleteSilently(originalPath);
            deleteSilently(encryptedTempPath);
            throw new DocumentStorageException("Error procesando el documento recibido", e);
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        long encryptedSize;
        try {
            encryptedSize = Files.size(encryptedTempPath);
        } catch (IOException e) {
            deleteSilently(encryptedTempPath);
            throw new DocumentStorageException("No se pudo determinar tamano de archivo cifrado", e);
        }

        Instant storedAt = Instant.now();
        DocumentRecord record = new DocumentRecord(
            documentId,
            senderId,
            recipientId,
            ownerScope,
            senderIp,
            documentType,
            safeFileName,
            originalPath.toString(),
            sha256,
            securityService.getEncryptionAlgorithmLabel(),
            iv,
            originalSize,
            storedAt
        );

        try {
            repository.save(record, encryptedTempPath, encryptedSize);
        } catch (RuntimeException ex) {
            deleteSilently(originalPath);
            throw ex;
        } finally {
            deleteSilently(encryptedTempPath);
        }

        activitySource.emitActivity("DOCUMENTO_RECIBIDO", String.format(
            "Documento %s (%s) recibido de %s para %s. Hash=%s",
            record.documentId(),
            record.documentType(),
            senderId,
            recipientId,
            record.sha256Hash()
        ));

        exchangeLogService.logReceived(record);

        return new StoredDocument(
            record.documentId(),
            record.documentType(),
            record.originalFileName(),
            record.originalFilePath(),
            record.sha256Hash(),
            record.originalSizeBytes(),
            record.createdAt()
        );
    }

    private DocumentType toDocumentType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("type es obligatorio");
        }
        try {
            return DocumentType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("type invalido. Use MESSAGE o FILE");
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("originalFileName no puede ser vacio");
        }
        String sanitized = Path.of(fileName).getFileName().toString();
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("originalFileName invalido");
        }
        return sanitized;
    }

    private void validateIdentity(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " no puede ser vacio");
        }
    }

    private String normalizeIp(String senderIp) {
        if (senderIp == null || senderIp.isBlank()) {
            return "127.0.0.1";
        }
        return senderIp.trim();
    }

    private DocumentOwnerScope resolveScope(String senderIp) {
        if ("127.0.0.1".equals(senderIp) || "localhost".equalsIgnoreCase(senderIp)) {
            return DocumentOwnerScope.LOCAL;
        }
        return DocumentOwnerScope.EXTERNAL;
    }

    private void deleteSilently(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // No interrumpe el flujo principal si la limpieza falla.
        }
    }
}

