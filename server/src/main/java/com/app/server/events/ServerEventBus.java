package com.app.server.events;

import com.app.server.net.ClientContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Bus de eventos interno asíncrono.
 *
 * - Asíncrono: usa un ExecutorService dedicado (single-thread) para que la
 *   publicación nunca bloquee al productor. Mantiene el orden de eventos.
 * - Desacoplado: los productores sólo conocen este bus y el tipo de evento;
 *   no saben nada de los listeners.
 * - Activable/Desactivable: se puede deshabilitar sin tocar ni un solo punto
 *   de publicación (cuando está deshabilitado {@link #publish} es un no-op).
 */
public class ServerEventBus {

    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;
    private volatile boolean enabled = true;

    public ServerEventBus() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "server-event-bus");
            t.setDaemon(true);
            return t;
        });
    }

    public void subscribe(ServerEventListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void unsubscribe(ServerEventListener listener) {
        listeners.remove(listener);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Publica un evento genérico.
     */
    public void publish(ServerEvent event) {
        if (!enabled || listeners.isEmpty()) return;
        try {
            executor.submit(() -> dispatch(event));
        } catch (Exception ignored) {
            // executor podría estar apagado durante el shutdown
        }
    }

    public void publish(ServerEventType tipo, String origen, String detalle) {
        publish(new ServerEvent(tipo, origen, detalle, null));
    }

    public void publish(ServerEventType tipo, ClientContext context, String detalle) {
        publish(new ServerEvent(tipo, context != null ? context.getProtocol() : null,
                detalle, context));
    }

    public void publishError(String origen, Throwable error, ClientContext context) {
        String detalle = error != null ? (error.getClass().getSimpleName() + ": " + error.getMessage()) : "error";
        publish(new ServerEvent(ServerEventType.ERROR, origen, detalle, context));
    }

    private void dispatch(ServerEvent event) {
        for (ServerEventListener l : listeners) {
            try {
                l.onEvent(event);
            } catch (Throwable t) {
                // un listener roto no debe tumbar al resto
                System.err.println("[EVT] listener falló: " + t.getMessage());
            }
        }
    }

    /**
     * Cierra el bus de forma ordenada, drenando eventos pendientes.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
