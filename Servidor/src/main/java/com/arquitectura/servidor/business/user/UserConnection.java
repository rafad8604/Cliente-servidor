package com.arquitectura.servidor.business.user;

import java.time.Instant;
import java.util.UUID;

public record UserConnection(String userId, String username, String ipAddress, Instant connectedAt) {

    public UserConnection {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId no puede ser nulo o vacio");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username no puede ser nulo o vacio");
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("ipAddress no puede ser nulo o vacio");
        }
        if (connectedAt == null) {
            throw new IllegalArgumentException("connectedAt no puede ser nulo");
        }
    }

    public static UserConnection of(String username, String ipAddress) {
        return new UserConnection(UUID.randomUUID().toString(), username, ipAddress, Instant.now());
    }
}

