package com.arquitectura.servidor.presentation.console;

import com.arquitectura.servidor.business.activity.ActivityEvent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleActivityObserverTest {

    @Test
    void shouldPrintEventDataInConsole() {
        ConsoleActivityObserver observer = new ConsoleActivityObserver();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            observer.update(new ActivityEvent(Instant.parse("2026-04-14T12:00:00Z"), "Servidor", "INICIO", "Mensaje"));
        } finally {
            System.setOut(originalOut);
        }

        String consoleLine = output.toString(StandardCharsets.UTF_8);
        assertTrue(consoleLine.contains("[Servidor] [INICIO] Mensaje"));
    }
}

