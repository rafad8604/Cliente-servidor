package com.arquitectura.servidor.business.document;

import java.nio.file.Path;
import java.util.List;

public interface DocumentRecordRepository {

    void save(DocumentRecord record, Path encryptedPayloadPath, long encryptedPayloadSizeBytes);

    List<AvailableDocumentInfo> findAllAvailable();

    DocumentPayload fetchPayload(String documentId, DocumentDeliveryMode mode);
}

