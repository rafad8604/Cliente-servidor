package com.arquitectura.servidor.business.document;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentReceptionServiceTest {

    private Path tempDir;

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    void shouldStoreOriginalFileAndPersistMetadata() throws Exception {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentReceptionService service = buildService(repository);

        byte[] payload = "contenido de prueba".getBytes(StandardCharsets.UTF_8);
        StoredDocument result = service.receiveFile(
            "Alice",
            "Bob",
            "10.0.0.10",
            "documento.txt",
            new ByteArrayInputStream(payload)
        );

        assertNotNull(result.documentId());
        assertTrue(Files.exists(Path.of(result.originalFilePath())));
        assertEquals(payload.length, result.originalSizeBytes());

        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        assertEquals(expectedHash, result.sha256Hash());

        assertNotNull(repository.lastRecord);
        assertTrue(repository.lastEncryptedPayload.length > 0);
        assertEquals(result.documentId(), repository.lastRecord.documentId());
    }

    @Test
    void shouldReceiveMessageFromJson() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentReceptionService service = buildService(repository);

        String json = """
            {
              "senderId": "Alice",
              "recipientId": "Bob",
              "type": "MESSAGE",
              "message": "Hola"
            }
            """;

        StoredDocument result = service.receiveFromJson(json, null);

        assertEquals(DocumentType.MESSAGE, result.documentType());
        assertNotNull(repository.lastRecord);
        assertEquals("Alice", repository.lastRecord.senderId());
        assertEquals("Bob", repository.lastRecord.recipientId());
    }

    private DocumentReceptionService buildService(InMemoryDocumentRepository repository) {
        DocumentProcessingConfig config = new DocumentProcessingConfig();
        tempDir = createTempDirectory();
        ReflectionTestUtils.setField(config, "originalDirectory", tempDir.toString());
        ReflectionTestUtils.setField(config, "encryptionSecret", "test-secret");
        ReflectionTestUtils.setField(config, "bufferSizeBytes", 8192);
        ReflectionTestUtils.invokeMethod(config, "init");

        DocumentSecurityService securityService = new DocumentSecurityService(config);

        return new DocumentReceptionService(
            config,
            securityService,
            repository,
            new DocumentExchangeLogService(config, new ObjectMapper()),
            new ServerActivitySource(),
            new ObjectMapper()
        );
    }

    private Path createTempDirectory() {
        try {
            return Files.createTempDirectory("document-reception-test-");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear directorio temporal para pruebas", e);
        }
    }

    private static class InMemoryDocumentRepository implements DocumentRecordRepository {
        private DocumentRecord lastRecord;
        private byte[] lastEncryptedPayload;

        @Override
        public void save(DocumentRecord record, Path encryptedPayloadPath, long encryptedPayloadSizeBytes) {
            this.lastRecord = record;
            try {
                this.lastEncryptedPayload = Files.readAllBytes(encryptedPayloadPath);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            assertEquals(lastEncryptedPayload.length, encryptedPayloadSizeBytes);
        }

        @Override
        public List<AvailableDocumentInfo> findAllAvailable() {
            return List.of();
        }

        @Override
        public DocumentPayload fetchPayload(String documentId, DocumentDeliveryMode mode) {
            throw new UnsupportedOperationException("No requerido en esta prueba");
        }
    }
}

