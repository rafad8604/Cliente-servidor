package com.arquitectura.servidor.presentation.console;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ServerConsoleRunner implements CommandLineRunner {

    private final ServerActivitySource activitySource;
    private final ConsoleActivityObserver consoleObserver;

    public ServerConsoleRunner(ServerActivitySource activitySource, ConsoleActivityObserver consoleObserver) {
        this.activitySource = activitySource;
        this.consoleObserver = consoleObserver;
    }

    @Override
    public void run(String... args) {
        activitySource.addObserver(consoleObserver);
        activitySource.emitActivity("INICIO", "Servidor de consola iniciado y monitoreando actividad.");
    }

    @PreDestroy
    public void onShutdown() {
        activitySource.emitActivity("CIERRE", "Servidor en proceso de apagado.");
        activitySource.removeObserver(consoleObserver);
    }
}

