package com.arquitectura.servidor.presentation.console;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ServerHeartbeatPublisher {

    private final ServerActivitySource activitySource;
    private final AtomicLong heartbeatCounter = new AtomicLong();

    public ServerHeartbeatPublisher(ServerActivitySource activitySource) {
        this.activitySource = activitySource;
    }

    @Scheduled(fixedDelayString = "${server.heartbeat.delay-ms:5000}")
    public void publishHeartbeat() {
        long heartbeat = heartbeatCounter.incrementAndGet();
        activitySource.emitActivity("HEARTBEAT", "Servidor activo. Pulso #" + heartbeat);
    }
}

