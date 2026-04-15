package com.arquitectura.servidor.business.document;

import java.time.Instant;

public record StoredDocument(String documentId,
                             DocumentType documentType,
                             String originalFileName,
                             String originalFilePath,
                             String sha256Hash,
                             long originalSizeBytes,
                             Instant storedAt) {
}

