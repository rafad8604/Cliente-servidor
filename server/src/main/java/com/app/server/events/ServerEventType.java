package com.app.server.events;

/**
 * Tipos de eventos internos del servidor sobre los que se puede observar.
 * Cubren los puntos de interes indicados por la especificacion del refactor.
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

    // Eventos peer-to-peer
    PEER_DESCUBIERTO,
    PEER_OFFLINE,
    PEER_HELLO_RECIBIDO,
    PEER_CONEXION_ENTRANTE,
    PEER_CATALOGO_ACTUALIZADO,
    PEER_DESCARGA_PROXY,
    PEER_ERROR,

    ERROR
}
