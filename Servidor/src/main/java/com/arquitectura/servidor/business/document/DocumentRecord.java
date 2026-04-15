package com.arquitectura.servidor.business.document;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record DocumentRecord(String documentId,
                             String senderId,
                             String recipientId,
                             DocumentOwnerScope ownerScope,
                             String ownerIp,
                             DocumentType documentType,
                             String originalFileName,
                             String originalFilePath,
                             String sha256Hash,
                             String encryptionAlgorithm,
                             byte[] initializationVector,
                             long originalSizeBytes,
                             Instant createdAt) {

    public DocumentRecord {
        Objects.requireNonNull(documentId, "documentId no puede ser nulo");
        Objects.requireNonNull(senderId, "senderId no puede ser nulo");
        Objects.requireNonNull(recipientId, "recipientId no puede ser nulo");
        Objects.requireNonNull(ownerScope, "ownerScope no puede ser nulo");
        Objects.requireNonNull(ownerIp, "ownerIp no puede ser nulo");
        Objects.requireNonNull(documentType, "documentType no puede ser nulo");
        Objects.requireNonNull(originalFileName, "originalFileName no puede ser nulo");
        Objects.requireNonNull(originalFilePath, "originalFilePath no puede ser nulo");
        Objects.requireNonNull(sha256Hash, "sha256Hash no puede ser nulo");
        Objects.requireNonNull(encryptionAlgorithm, "encryptionAlgorithm no puede ser nulo");
        Objects.requireNonNull(initializationVector, "initializationVector no puede ser nulo");
        Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");

        if (documentId.isBlank()) {
            throw new IllegalArgumentException("documentId no puede ser vacio");
        }
        if (senderId.isBlank()) {
            throw new IllegalArgumentException("senderId no puede ser vacio");
        }
        if (recipientId.isBlank()) {
            throw new IllegalArgumentException("recipientId no puede ser vacio");
        }
        if (ownerIp.isBlank()) {
            throw new IllegalArgumentException("ownerIp no puede ser vacio");
        }
        if (originalFileName.isBlank()) {
            throw new IllegalArgumentException("originalFileName no puede ser vacio");
        }
        if (originalFilePath.isBlank()) {
            throw new IllegalArgumentException("originalFilePath no puede ser vacio");
        }
        if (sha256Hash.length() != 64) {
            throw new IllegalArgumentException("sha256Hash debe tener 64 caracteres hexadecimales");
        }
        if (originalSizeBytes < 0) {
            throw new IllegalArgumentException("originalSizeBytes no puede ser negativo");
        }
    }

    @Override
    public byte[] initializationVector() {
        return Arrays.copyOf(initializationVector, initializationVector.length);
    }
}

