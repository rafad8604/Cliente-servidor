package com.app.shared.protocol;

/**
 * Comandos del protocolo de comunicación entre cliente y servidor.
 */
public enum Comando {
    // Comandos del cliente al servidor
    ENVIAR_ARCHIVO,
    ENVIAR_MENSAJE,
    LISTAR_DOCUMENTOS,
    DESCARGAR_ARCHIVO,
    DESCARGAR_HASH,
    DESCARGAR_ENCRIPTADO,
    LISTAR_CLIENTES,

    // Comandos del servidor al cliente
    RESPUESTA,
    ERROR,
    CHAT_MENSAJE,
    SESION_INFO
}
