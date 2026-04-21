package com.app.server.events;

/**
 * Observador de eventos del servidor.
 * Las implementaciones reciben eventos desde un hilo dedicado del
 * {@link ServerEventBus} y no deben ejecutar operaciones bloqueantes largas.
 */
@FunctionalInterface
public interface ServerEventListener {
    void onEvent(ServerEvent event);
}
