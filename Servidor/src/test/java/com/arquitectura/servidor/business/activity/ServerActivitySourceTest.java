package com.arquitectura.servidor.business.activity;

import com.arquitectura.servidor.business.observer.Observer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerActivitySourceTest {

    @Test
    void shouldNotifyRegisteredObservers() {
        ServerActivitySource source = new ServerActivitySource();
        RecordingObserver observer = new RecordingObserver();

        source.addObserver(observer);
        source.notifyObservers(new ActivityEvent(Instant.now(), "Servidor", "PRUEBA", "Evento de prueba"));

        assertEquals(1, observer.events.size());
        assertEquals("PRUEBA", observer.events.getFirst().type());
    }

    @Test
    void shouldNotNotifyRemovedObserver() {
        ServerActivitySource source = new ServerActivitySource();
        RecordingObserver observer = new RecordingObserver();

        source.addObserver(observer);
        source.removeObserver(observer);
        source.notifyObservers(new ActivityEvent(Instant.now(), "Servidor", "PRUEBA", "No debe llegar"));

        assertEquals(0, observer.events.size());
    }

    private static class RecordingObserver implements Observer {

        private final List<ActivityEvent> events = new ArrayList<>();

        @Override
        public void update(ActivityEvent event) {
            events.add(event);
        }
    }
}

