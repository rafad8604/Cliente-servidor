package com.app.shared.protocol;

/**
 * Comandos del protocolo de comunicacion entre cliente-servidor y entre servidores (peers).
 */
public enum Comando {
    // Comandos cliente -> servidor
    ENVIAR_ARCHIVO,
    ENVIAR_MENSAJE,
    LISTAR_DOCUMENTOS,
    DESCARGAR_ARCHIVO,
    DESCARGAR_HASH,
    DESCARGAR_ENCRIPTADO,
    LISTAR_CLIENTES,
    LISTAR_SERVIDORES,
    OBTENER_EVENTOS,
    OBTENER_LOGS,

    // Comandos servidor -> cliente
    RESPUESTA,
    ERROR,
    CHAT_MENSAJE,
    SESION_INFO,

    // Comandos peer-to-peer (servidor <-> servidor)
    PEER_HELLO,
    PEER_BYE,
    PEER_PING,
    PEER_LISTAR_DOCS,
    PEER_DESCARGAR_ARCHIVO,
    PEER_DESCARGAR_HASH
}
