package com.app.server.events;

import com.app.server.net.ClientContext;

import java.time.LocalDateTime;

/**
 * Evento inmutable publicado en el {@link ServerEventBus}.
 * Los listeners reciben la instancia y deciden cómo mostrarla/procesarla.
 */
public final class ServerEvent {

    private final ServerEventType tipo;
    private final String origen;
    private final String detalle;
    private final LocalDateTime timestamp;
    private final ClientContext clientContext;

    public ServerEvent(ServerEventType tipo, String origen, String detalle, ClientContext context) {
        this.tipo = tipo;
        this.origen = origen;
        this.detalle = detalle;
        this.clientContext = context;
        this.timestamp = LocalDateTime.now();
    }

    public ServerEventType getTipo() {
        return tipo;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDetalle() {
        return detalle;
    }

    public ClientContext getClientContext() {
        return clientContext;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[EVT ").append(timestamp).append("] ").append(tipo.name());
        if (origen != null) sb.append(" | origen=").append(origen);
        if (clientContext != null) sb.append(" | cliente=").append(clientContext);
        if (detalle != null && !detalle.isEmpty()) sb.append(" | ").append(detalle);
        return sb.toString();
    }
}
