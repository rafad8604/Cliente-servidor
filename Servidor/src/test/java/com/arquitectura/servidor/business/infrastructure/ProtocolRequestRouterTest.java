package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import com.arquitectura.servidor.business.document.AvailableDocumentInfo;
import com.arquitectura.servidor.business.document.DocumentDeliveryMode;
import com.arquitectura.servidor.business.document.DocumentExchangeLogService;
import com.arquitectura.servidor.business.document.DocumentOwnerScope;
import com.arquitectura.servidor.business.document.DocumentPayload;
import com.arquitectura.servidor.business.document.DocumentProcessingConfig;
import com.arquitectura.servidor.business.document.DocumentReceptionService;
import com.arquitectura.servidor.business.document.DocumentRecord;
import com.arquitectura.servidor.business.document.DocumentRecordRepository;
import com.arquitectura.servidor.business.document.DocumentSecurityService;
import com.arquitectura.servidor.business.user.UserConnectionConfig;
import com.arquitectura.servidor.business.user.UserConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolRequestRouterTest {

    private Path tempDir;

    @AfterEach
    void cleanup() throws IOException {
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
    void shouldRouteConnectedClientsCount() {
        ProtocolRequestRouter router = buildRouter();

        ProtocolResponse response = router.route("{\"service\":\"CONNECTED_CLIENTS_COUNT\"}", null, "127.0.0.1");

        assertTrue(response.json().contains("\"service\":\"CONNECTED_CLIENTS_COUNT\""));
        assertTrue(response.json().contains("\"connectedClients\":"));
    }

    @Test
    void shouldReturnErrorForUnsupportedService() {
        ProtocolRequestRouter router = buildRouter();

        ProtocolResponse response = router.route("{\"service\":\"UNKNOWN\"}", null, "127.0.0.1");

        assertTrue(response.json().contains("\"service\":\"ERROR\""));
    }

    @Test
    void shouldRouteSendDocumentOriginalWithHash() {
        ProtocolRequestRouter router = buildRouter();

        ProtocolResponse response = router.route(
            "{\"service\":\"SEND_DOCUMENT\",\"documentId\":\"doc-1\",\"mode\":\"original_with_hash\"}",
            null,
            "10.0.0.77"
        );

        assertTrue(response.json().contains("\"service\":\"SEND_DOCUMENT\""));
        assertTrue(response.json().contains("\"mode\":\"ORIGINAL_WITH_HASH\""));
        assertNotNull(response.binaryPayload());
    }

    private ProtocolRequestRouter buildRouter() {
        ObjectMapper mapper = new ObjectMapper();

        UserConnectionConfig userConfig = new UserConnectionConfig();
        userConfig.setMaxUsers(5);
        UserConnectionService userConnectionService = new UserConnectionService(userConfig, new ServerActivitySource());
        ClientConnectionQueryService clientConnectionQueryService = new ClientConnectionQueryService(userConnectionService, mapper);

        DocumentProcessingConfig docConfig = new DocumentProcessingConfig();
        tempDir = createTempDirectory();
        ReflectionTestUtils.setField(docConfig, "originalDirectory", tempDir.toString());
        ReflectionTestUtils.setField(docConfig, "encryptionSecret", "router-test-secret");
        ReflectionTestUtils.setField(docConfig, "bufferSizeBytes", 8192);
        ReflectionTestUtils.invokeMethod(docConfig, "init");

        DocumentSecurityService securityService = new DocumentSecurityService(docConfig);
        DocumentExchangeLogService logService = new DocumentExchangeLogService(docConfig, mapper);
        DocumentRecordRepository repository = new RouterRepositoryStub();

        DocumentReceptionService receptionService = new DocumentReceptionService(
            docConfig,
            securityService,
            repository,
            logService,
            new ServerActivitySource(),
            mapper
        );

        ClientDocumentReceptionService clientDocumentReceptionService = new ClientDocumentReceptionService(receptionService, mapper);
        ClientDocumentQueryService clientDocumentQueryService = new ClientDocumentQueryService(repository, logService, mapper);

        return new ProtocolRequestRouter(
            clientConnectionQueryService,
            clientDocumentReceptionService,
            clientDocumentQueryService,
            mapper
        );
    }

    private Path createTempDirectory() {
        try {
            return Files.createTempDirectory("router-test-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class RouterRepositoryStub implements DocumentRecordRepository {

        @Override
        public void save(DocumentRecord record, Path encryptedPayloadPath, long encryptedPayloadSizeBytes) {
        }

        @Override
        public List<AvailableDocumentInfo> findAllAvailable() {
            return List.of(new AvailableDocumentInfo("doc-1", "archivo.txt", 123L, "txt", DocumentOwnerScope.LOCAL, "127.0.0.1"));
        }

        @Override
        public DocumentPayload fetchPayload(String documentId, DocumentDeliveryMode mode) {
            return new DocumentPayload(
                documentId,
                "archivo.txt",
                8,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "AES/GCM",
                new ByteArrayInputStream("contenido".getBytes())
            );
        }
    }
}

