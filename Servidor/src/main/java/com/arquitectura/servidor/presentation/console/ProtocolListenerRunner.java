package com.arquitectura.servidor.presentation.console;

import com.arquitectura.servidor.business.infrastructure.CommunicationService;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ProtocolListenerRunner implements CommandLineRunner {

    private final CommunicationService communicationService;

    public ProtocolListenerRunner(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @Override
    public void run(String... args) throws Exception {
        communicationService.startListening();
    }

    @PreDestroy
    public void onShutdown() {
        communicationService.stopListening();
    }
}

