package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.document.DocumentProcessingConfig;
import com.arquitectura.servidor.business.document.DocumentReceptionService;
import com.arquitectura.servidor.business.document.DocumentRecord;
import com.arquitectura.servidor.business.document.DocumentRecordRepository;
import com.arquitectura.servidor.business.document.DocumentSecurityService;
import com.arquitectura.servidor.business.document.DocumentExchangeLogService;
import com.arquitectura.servidor.business.document.DocumentType;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientDocumentReceptionServiceTest {

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
    void shouldReturnJsonResponseWhenReceivingFile() {
        DocumentReceptionService receptionService = buildReceptionService();
        ClientDocumentReceptionService service = new ClientDocumentReceptionService(receptionService, new ObjectMapper());

        String metadata = """
            {
              "senderId": "Alice",
              "recipientId": "Bob",
              "type": "FILE",
              "fileName": "ejemplo.txt"
            }
            """;

        String response = service.receiveDocumentJsonResponse(
            metadata,
            new ByteArrayInputStream("archivo demo".getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(response.contains("\"service\":\"DOCUMENT_RECEIVED\""));
        assertTrue(response.contains("\"type\":\"FILE\""));
        assertTrue(response.contains("\"hash\":"));
    }

    private DocumentReceptionService buildReceptionService() {
        DocumentProcessingConfig config = new DocumentProcessingConfig();
        tempDir = createTempDirectory();
        ReflectionTestUtils.setField(config, "originalDirectory", tempDir.toString());
        ReflectionTestUtils.setField(config, "encryptionSecret", "test-secret");
        ReflectionTestUtils.setField(config, "bufferSizeBytes", 8192);
        ReflectionTestUtils.invokeMethod(config, "init");

        DocumentSecurityService securityService = new DocumentSecurityService(config);
        DocumentRecordRepository repository = new NoOpRepository();

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
            return Files.createTempDirectory("client-document-test-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class NoOpRepository implements DocumentRecordRepository {
        @Override
        public void save(DocumentRecord record, Path encryptedPayloadPath, long encryptedPayloadSizeBytes) {
            if (record.documentType() != DocumentType.FILE && record.documentType() != DocumentType.MESSAGE) {
                throw new IllegalStateException("Tipo no soportado");
            }
        }

        @Override
        public List<com.arquitectura.servidor.business.document.AvailableDocumentInfo> findAllAvailable() {
            return List.of();
        }

        @Override
        public com.arquitectura.servidor.business.document.DocumentPayload fetchPayload(
            String documentId,
            com.arquitectura.servidor.business.document.DocumentDeliveryMode mode
        ) {
            throw new UnsupportedOperationException("No requerido en esta prueba");
        }
    }
}

