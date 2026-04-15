package com.arquitectura.servidor.business.activity;

import java.time.Instant;
import java.util.Objects;

public record ActivityEvent(Instant timestamp, String source, String type, String message) {

    public ActivityEvent {
        Objects.requireNonNull(timestamp, "timestamp no puede ser nulo");
        Objects.requireNonNull(source, "source no puede ser nulo");
        Objects.requireNonNull(type, "type no puede ser nulo");
        Objects.requireNonNull(message, "message no puede ser nulo");
    }
}

