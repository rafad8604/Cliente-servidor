package com.arquitectura.servidor.business.user;

import java.time.LocalDate;
import java.time.LocalTime;

public record ConnectedClientInfo(String ipAddress, LocalDate startDate, LocalTime startTime) {

    public ConnectedClientInfo {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("ipAddress no puede ser nulo o vacio");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("startDate no puede ser nulo");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime no puede ser nulo");
        }
    }
}

