package com.arquitectura.servidor.business.document;

public record AvailableDocumentInfo(String documentId,
                                    String name,
                                    long sizeBytes,
                                    String extension,
                                    DocumentOwnerScope ownerScope,
                                    String ownerIp) {
}

