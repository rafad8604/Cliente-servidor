package com.app.server.events;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Listener que guarda los eventos recientes en memoria para que puedan
 * consultarse via HTTP (o consola) sin tener que leer el archivo de log.
 *
 * <p>Ring buffer con capacidad fija; al llegar al limite descarta el evento
 * mas antiguo.</p>
 */
public class InMemoryEventBuffer implements ServerEventListener {

    public static final int DEFAULT_CAPACITY = 500;

    private final int capacity;
    private final Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();

    public InMemoryEventBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public InMemoryEventBuffer(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void onEvent(ServerEvent event) {
        events.addLast(event);
        while (events.size() > capacity) {
            events.pollFirst();
        }
    }

    /**
     * Snapshot de los ultimos {@code limit} eventos (mas recientes primero).
     */
    public List<ServerEvent> snapshot(int limit) {
        ServerEvent[] arr = events.toArray(new ServerEvent[0]);
        int from = Math.max(0, arr.length - limit);
        List<ServerEvent> out = new ArrayList<>(arr.length - from);
        for (int i = arr.length - 1; i >= from; i--) {
            out.add(arr[i]);
        }
        return out;
    }

    public int size() {
        return events.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        events.clear();
    }
}
