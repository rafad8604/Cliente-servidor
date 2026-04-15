package com.arquitectura.servidor.persistence.document;

import com.arquitectura.servidor.business.document.DocumentRecord;
import com.arquitectura.servidor.business.document.DocumentRecordRepository;
import com.arquitectura.servidor.business.document.DocumentStorageException;
import com.arquitectura.servidor.business.document.AvailableDocumentInfo;
import com.arquitectura.servidor.business.document.DocumentDeliveryMode;
import com.arquitectura.servidor.business.document.DocumentOwnerScope;
import com.arquitectura.servidor.business.document.DocumentPayload;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MysqlDocumentRecordRepository implements DocumentRecordRepository {

    private static final String INSERT_SQL = """
        INSERT INTO received_documents (
            document_id,
            sender_id,
            recipient_id,
            owner_scope,
            owner_ip,
            document_type,
            original_file_name,
            original_file_path,
            sha256_hash,
            encryption_algorithm,
            iv,
            encrypted_payload,
            encrypted_size_bytes,
            original_size_bytes,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_ALL_SQL = """
        SELECT document_id, original_file_name, original_size_bytes, owner_scope, owner_ip
        FROM received_documents
        ORDER BY created_at DESC
        """;

    private static final String SELECT_METADATA_SQL = """
        SELECT document_id, original_file_name, original_file_path, sha256_hash, encryption_algorithm,
               encrypted_size_bytes, original_size_bytes
        FROM received_documents
        WHERE document_id = ?
        """;

    private static final String SELECT_ENCRYPTED_SQL = """
        SELECT encrypted_payload
        FROM received_documents
        WHERE document_id = ?
        """;

    private final DataSource dataSource;

    public MysqlDocumentRecordRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(DocumentRecord record, Path encryptedPayloadPath, long encryptedPayloadSizeBytes) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
             InputStream encryptedPayload = Files.newInputStream(encryptedPayloadPath)) {

            statement.setString(1, record.documentId());
            statement.setString(2, record.senderId());
            statement.setString(3, record.recipientId());
            statement.setString(4, record.ownerScope().name());
            statement.setString(5, record.ownerIp());
            statement.setString(6, record.documentType().name());
            statement.setString(7, record.originalFileName());
            statement.setString(8, record.originalFilePath());
            statement.setString(9, record.sha256Hash());
            statement.setString(10, record.encryptionAlgorithm());
            statement.setBytes(11, record.initializationVector());
            statement.setBinaryStream(12, encryptedPayload, encryptedPayloadSizeBytes);
            statement.setLong(13, encryptedPayloadSizeBytes);
            statement.setLong(14, record.originalSizeBytes());
            statement.setTimestamp(15, java.sql.Timestamp.from(record.createdAt()));
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new DocumentStorageException("No se pudo persistir el documento cifrado en MySQL", e);
        }
    }

    @Override
    public List<AvailableDocumentInfo> findAllAvailable() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = statement.executeQuery()) {

            List<AvailableDocumentInfo> documents = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("original_file_name");
                documents.add(new AvailableDocumentInfo(
                    rs.getString("document_id"),
                    name,
                    rs.getLong("original_size_bytes"),
                    extension(name),
                    DocumentOwnerScope.valueOf(rs.getString("owner_scope")),
                    rs.getString("owner_ip")
                ));
            }
            return documents;
        } catch (SQLException e) {
            throw new DocumentStorageException("No se pudo consultar listado de documentos", e);
        }
    }

    @Override
    public DocumentPayload fetchPayload(String documentId, DocumentDeliveryMode mode) {
        Metadata metadata = fetchMetadata(documentId);
        if (mode == DocumentDeliveryMode.ENCRYPTED) {
            return loadEncryptedPayload(documentId, metadata);
        }

        Path path = Path.of(metadata.originalFilePath());
        try {
            InputStream input = Files.newInputStream(path, StandardOpenOption.READ);
            return new DocumentPayload(
                metadata.documentId(),
                metadata.originalFileName(),
                metadata.originalSizeBytes(),
                metadata.sha256Hash(),
                metadata.encryptionAlgorithm(),
                input
            );
        } catch (IOException e) {
            throw new DocumentStorageException("No se pudo leer archivo original para envio", e);
        }
    }

    private DocumentPayload loadEncryptedPayload(String documentId, Metadata metadata) {
        Path tempFile;
        try {
            tempFile = Files.createTempFile("payload-enc-" + documentId + "-", ".bin");
        } catch (IOException e) {
            throw new DocumentStorageException("No se pudo preparar archivo temporal de envio", e);
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ENCRYPTED_SQL)) {
            statement.setString(1, documentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("No existe documento con id: " + documentId);
                }
                try (InputStream encrypted = rs.getBinaryStream("encrypted_payload");
                     OutputStream output = Files.newOutputStream(tempFile, StandardOpenOption.WRITE)) {
                    encrypted.transferTo(output);
                }
            }

            InputStream payloadStream = new DeleteOnCloseFileInputStream(tempFile);
            return new DocumentPayload(
                metadata.documentId(),
                metadata.originalFileName() + ".enc",
                metadata.encryptedSizeBytes(),
                metadata.sha256Hash(),
                metadata.encryptionAlgorithm(),
                payloadStream
            );
        } catch (SQLException | IOException e) {
            deleteSilently(tempFile);
            throw new DocumentStorageException("No se pudo recuperar payload cifrado para envio", e);
        }
    }

    private Metadata fetchMetadata(String documentId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_METADATA_SQL)) {
            statement.setString(1, documentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("No existe documento con id: " + documentId);
                }
                return new Metadata(
                    rs.getString("document_id"),
                    rs.getString("original_file_name"),
                    rs.getString("original_file_path"),
                    rs.getString("sha256_hash"),
                    rs.getString("encryption_algorithm"),
                    rs.getLong("encrypted_size_bytes"),
                    rs.getLong("original_size_bytes")
                );
            }
        } catch (SQLException e) {
            throw new DocumentStorageException("No se pudo leer metadatos del documento", e);
        }
    }

    private String extension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx == -1 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase();
    }

    private void deleteSilently(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record Metadata(String documentId,
                            String originalFileName,
                            String originalFilePath,
                            String sha256Hash,
                            String encryptionAlgorithm,
                            long encryptedSizeBytes,
                            long originalSizeBytes) {
    }
}

