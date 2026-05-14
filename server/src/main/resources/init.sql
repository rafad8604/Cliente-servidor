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
    protocolo VARCHAR(10) NOT NULL COMMENT 'TCP, UDP o HTTP',
    fecha_inicio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nombre VARCHAR(100) NOT NULL DEFAULT '',
    PRIMARY KEY (ip, puerto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE clientes_conectados
    MODIFY COLUMN protocolo VARCHAR(10) NOT NULL COMMENT 'TCP, UDP o HTTP';

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
    envio_alcance ENUM('TODOS','DIRIGIDO') NOT NULL DEFAULT 'TODOS',
    dest_ip VARCHAR(45) NULL,
    dest_puerto INT NULL,
    dest_protocolo VARCHAR(10) NULL,
    origen_servidor_etiqueta VARCHAR(500) NULL,
    origen_peer_id VARCHAR(64) NULL,
    remitente_nombre VARCHAR(200) NULL,
    remitente_puerto INT NULL,
    remitente_protocolo VARCHAR(10) NULL,
    INDEX idx_ip_propietario (ip_propietario),
    INDEX idx_tipo (tipo),
    INDEX idx_envio_alcance (envio_alcance),
    INDEX idx_dest_cliente (dest_ip, dest_puerto, dest_protocolo)
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

-- Migración idempotente para BDs existentes (compatible con MySQL 8.0+)
DROP PROCEDURE IF EXISTS agregar_columna_si_no_existe;
DELIMITER $$
CREATE PROCEDURE agregar_columna_si_no_existe(
    IN p_tabla VARCHAR(64),
    IN p_columna VARCHAR(64),
    IN p_definicion TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_tabla
          AND COLUMN_NAME = p_columna
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_tabla, '` ADD COLUMN `', p_columna, '` ', p_definicion);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL agregar_columna_si_no_existe('documentos_chunks', 'codificacion', "VARCHAR(16) NOT NULL DEFAULT 'RAW'");
CALL agregar_columna_si_no_existe('clientes_conectados', 'nombre', "VARCHAR(100) NOT NULL DEFAULT ''");

CALL agregar_columna_si_no_existe('documentos', 'envio_alcance', "ENUM('TODOS','DIRIGIDO') NOT NULL DEFAULT 'TODOS'");
CALL agregar_columna_si_no_existe('documentos', 'dest_ip', "VARCHAR(45) NULL");
CALL agregar_columna_si_no_existe('documentos', 'dest_puerto', "INT NULL");
CALL agregar_columna_si_no_existe('documentos', 'dest_protocolo', "VARCHAR(10) NULL");
CALL agregar_columna_si_no_existe('documentos', 'origen_servidor_etiqueta', "VARCHAR(500) NULL");
CALL agregar_columna_si_no_existe('documentos', 'origen_peer_id', "VARCHAR(64) NULL");
CALL agregar_columna_si_no_existe('documentos', 'remitente_nombre', "VARCHAR(200) NULL");
CALL agregar_columna_si_no_existe('documentos', 'remitente_puerto', "INT NULL");
CALL agregar_columna_si_no_existe('documentos', 'remitente_protocolo', "VARCHAR(10) NULL");

DROP PROCEDURE IF EXISTS agregar_columna_si_no_existe;
