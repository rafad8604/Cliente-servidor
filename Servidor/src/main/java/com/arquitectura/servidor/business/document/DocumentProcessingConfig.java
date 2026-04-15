package com.arquitectura.servidor.business.document;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@PropertySource("classpath:document.properties")
public class DocumentProcessingConfig {

    @Value("${server.documents.original-dir:originalFiles}")
    private String originalDirectory;

    @Value("${server.documents.encryption-secret:change-me}")
    private String encryptionSecret;

    @Value("${server.documents.buffer-size-bytes:1048576}")
    private int bufferSizeBytes;

    private Path originalDirectoryPath;

    @PostConstruct
    void init() {
        if (encryptionSecret == null || encryptionSecret.isBlank()) {
            throw new IllegalArgumentException("server.documents.encryption-secret no puede ser vacio");
        }
        if (bufferSizeBytes < 8192) {
            throw new IllegalArgumentException("server.documents.buffer-size-bytes debe ser >= 8192");
        }

        originalDirectoryPath = Paths.get(originalDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(originalDirectoryPath);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear directorio de originales: " + originalDirectoryPath, e);
        }
    }

    public Path getOriginalDirectoryPath() {
        return originalDirectoryPath;
    }

    public String getEncryptionSecret() {
        return encryptionSecret;
    }

    public int getBufferSizeBytes() {
        return bufferSizeBytes;
    }
}

