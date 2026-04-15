CREATE TABLE IF NOT EXISTS received_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    sender_id VARCHAR(100) NOT NULL,
    recipient_id VARCHAR(100) NOT NULL,
    owner_scope VARCHAR(20) NOT NULL,
    owner_ip VARCHAR(64) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    original_file_path VARCHAR(1024) NOT NULL,
    sha256_hash CHAR(64) NOT NULL,
    encryption_algorithm VARCHAR(120) NOT NULL,
    iv VARBINARY(32) NOT NULL,
    encrypted_payload LONGBLOB NOT NULL,
    encrypted_size_bytes BIGINT NOT NULL,
    original_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_received_documents_document_id (document_id),
    KEY idx_received_documents_sender (sender_id),
    KEY idx_received_documents_recipient (recipient_id),
    KEY idx_received_documents_created_at (created_at)
);

