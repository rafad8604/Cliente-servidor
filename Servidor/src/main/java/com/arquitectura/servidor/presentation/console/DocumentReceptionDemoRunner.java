package com.arquitectura.servidor.presentation.console;

import com.arquitectura.servidor.business.infrastructure.ClientDocumentReceptionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "server.documents.demo.enabled", havingValue = "true")
public class DocumentReceptionDemoRunner implements CommandLineRunner {

    private final ClientDocumentReceptionService clientDocumentReceptionService;

    public DocumentReceptionDemoRunner(ClientDocumentReceptionService clientDocumentReceptionService) {
        this.clientDocumentReceptionService = clientDocumentReceptionService;
    }

    @Override
    public void run(String... args) {
        String metadata = """
            {
              "senderId": "Alice",
              "recipientId": "Bob",
              "type": "MESSAGE",
              "message": "Hola Bob, te envio este mensaje desde el demo"
            }
            """;

        String response = clientDocumentReceptionService.receiveDocumentJsonResponse(
            metadata,
            new ByteArrayInputStream(new byte[0])
        );

        System.out.println("Respuesta demo de recepcion de documento: " + response);

        String metadataFile = """
            {
              "senderId": "Alice",
              "recipientId": "Bob",
              "type": "FILE",
              "fileName": "nota.txt"
            }
            """;

        String fileResponse = clientDocumentReceptionService.receiveDocumentJsonResponse(
            metadataFile,
            new ByteArrayInputStream("Contenido de archivo demo".getBytes(StandardCharsets.UTF_8))
        );

        System.out.println("Respuesta demo de recepcion de archivo: " + fileResponse);
    }
}

