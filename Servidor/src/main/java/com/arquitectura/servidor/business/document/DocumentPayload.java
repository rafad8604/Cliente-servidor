package com.arquitectura.servidor.business.document;

import java.io.InputStream;

public record DocumentPayload(String documentId,
                              String fileName,
                              long payloadSizeBytes,
                              String sha256Hash,
                              String encryptionAlgorithm,
                              InputStream payloadStream) {
}

