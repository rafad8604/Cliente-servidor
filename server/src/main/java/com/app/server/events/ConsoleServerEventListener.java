package com.app.server.events;

import java.time.format.DateTimeFormatter;

/**
 * Listener de eventos: imprime con formato compacto y categorizado.
 *
 * <p>Formato: {@code [HH:mm:ss] CATEGORIA TIPO | detalles}, donde CATEGORIA es
 * una etiqueta corta deducida del {@link ServerEventType} (NET, POOL, PEER,
 * STOR, MSG, ERR) para facilitar lectura.</p>
 */
public class ConsoleServerEventListener implements ServerEventListener {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onEvent(ServerEvent event) {
        StringBuilder sb = new StringBuilder(96);
        sb.append('[').append(HORA.format(event.getTimestamp())).append("] ");
        sb.append(categoriaDe(event.getTipo())).append(' ').append(event.getTipo().name());
        if (event.getClientContext() != null) {
            sb.append(" | cliente=").append(event.getClientContext());
        } else if (event.getOrigen() != null) {
            sb.append(" | ").append(event.getOrigen());
        }
        if (event.getDetalle() != null && !event.getDetalle().isEmpty()) {
            sb.append(" | ").append(event.getDetalle());
        }
        System.out.println(sb);
    }

    private static String categoriaDe(ServerEventType t) {
        return switch (t) {
            case SERVIDOR_INICIADO, SERVIDOR_DETENIDO -> "[SYS]";
            case TCP_CONEXION_ABIERTA, TCP_CONEXION_CERRADA, TCP_CONEXION_RECHAZADA,
                    UDP_SESION_INICIADA, UDP_SESION_FINALIZADA, UDP_CONEXION_RECHAZADA -> "[NET]";
            case POOL_ADQUIRIDO, POOL_LIBERADO -> "[POOL]";
            case ARCHIVO_RECIBIDO, ALMACENAMIENTO_LOCAL, CHUNK_CREADO, CHUNK_BASE64,
                    CHUNK_PERSISTIDO, DOCUMENTO_RECONSTRUIDO -> "[STOR]";
            case MENSAJE_RECIBIDO -> "[MSG]";
            case PEER_DESCUBIERTO, PEER_OFFLINE, PEER_HELLO_RECIBIDO,
                    PEER_CONEXION_ENTRANTE, PEER_CATALOGO_ACTUALIZADO,
                    PEER_DESCARGA_PROXY -> "[PEER]";
            case PEER_ERROR, ERROR -> "[ERR]";
        };
    }
}
