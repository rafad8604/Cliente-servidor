package com.app.server.events;

/**
 * Tipos de eventos internos del servidor sobre los que se puede observar.
 * Cubren los puntos de interés indicados por la especificación del refactor.
 */
public enum ServerEventType {
    SERVIDOR_INICIADO,
    SERVIDOR_DETENIDO,

    TCP_CONEXION_ABIERTA,
    TCP_CONEXION_CERRADA,
    TCP_CONEXION_RECHAZADA,

    UDP_SESION_INICIADA,
    UDP_SESION_FINALIZADA,
    UDP_CONEXION_RECHAZADA,

    POOL_ADQUIRIDO,
    POOL_LIBERADO,

    ARCHIVO_RECIBIDO,
    ALMACENAMIENTO_LOCAL,
    CHUNK_CREADO,
    CHUNK_BASE64,
    CHUNK_PERSISTIDO,

    DOCUMENTO_RECONSTRUIDO,
    MENSAJE_RECIBIDO,

    ERROR
}
