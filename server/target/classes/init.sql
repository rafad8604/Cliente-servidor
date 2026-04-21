-- ============================================
-- Base de datos: mensajeria_db
-- Esquema completo para el servidor
-- ============================================

USE mensajeria_db;

-- Tabla de logs del sistema
CREATE TABLE IF NOT EXISTS logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    accion VARCHAR(100) NOT NULL,
    ip_origen VARCHAR(45) NOT NULL,
    fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detalles TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tabla de clientes actualmente conectados
CREATE TABLE IF NOT EXISTS clientes_conectados (
    ip VARCHAR(45) NOT NULL,
    puerto INT NOT NULL,
    protocolo VARCHAR(3) NOT NULL COMMENT 'TCP o UDP',
    fecha_inicio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ip, puerto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tabla principal de documentos (metadatos)
CREATE TABLE IF NOT EXISTS documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(500) NOT NULL,
    extension VARCHAR(50),
    tamano BIGINT NOT NULL COMMENT 'Tamaño en bytes',
    ruta_local_original VARCHAR(1000),
    hash_sha256 VARCHAR(64) NOT NULL,
    ip_propietario VARCHAR(45) NOT NULL,
    tipo ENUM('MENSAJE', 'ARCHIVO') NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ip_propietario (ip_propietario),
    INDEX idx_tipo (tipo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tabla de chunks (1 tabla para todos los chunks del documento)
-- La columna 'codificacion' identifica cómo están guardados los bytes:
--   RAW    → bytes encriptados sin codificar (formato legado)
--   BASE64 → bytes encriptados codificados en Base64 (flujo actual)
CREATE TABLE IF NOT EXISTS documentos_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    documento_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    datos_encriptados LONGBLOB NOT NULL,
    codificacion VARCHAR(16) NOT NULL DEFAULT 'RAW',
    FOREIGN KEY (documento_id) REFERENCES documentos(id) ON DELETE CASCADE,
    UNIQUE KEY uk_doc_chunk (documento_id, chunk_index),
    INDEX idx_documento_id (documento_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migración idempotente para BDs existentes (MySQL 8.0.29+)
ALTER TABLE documentos_chunks
    ADD COLUMN IF NOT EXISTS codificacion VARCHAR(16) NOT NULL DEFAULT 'RAW';
