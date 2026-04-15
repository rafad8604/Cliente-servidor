package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CommunicationService {

    private final ProtocolConfig protocolConfig;
    private final Map<CommunicationProtocol, ProtocolAdapter> adaptersByProtocol;
    private final ServerActivitySource activitySource;
    private ProtocolAdapter activeAdapter;

    public CommunicationService(ProtocolConfig protocolConfig,
                                List<ProtocolAdapter> adapters,
                                ServerActivitySource activitySource) {
        this.protocolConfig = protocolConfig;
        this.adaptersByProtocol = new EnumMap<>(CommunicationProtocol.class);
        for (ProtocolAdapter adapter : adapters) {
            adaptersByProtocol.put(adapter.protocol(), adapter);
        }
        this.activitySource = activitySource;
    }

    public void startListening() {
        if (isListening()) {
            activitySource.emitActivity("PROTOCOLO", "Servidor ya esta escuchando en " + getServerInfo());
            return;
        }

        CommunicationProtocol protocol = protocolConfig.getProtocol();
        activeAdapter = adaptersByProtocol.get(protocol);
        if (activeAdapter == null) {
            throw new IllegalStateException("No existe adapter registrado para protocolo: " + protocol);
        }
        activeAdapter.start(protocolConfig.getPort());

        String message = String.format("Servidor escuchando en puerto %d usando %s (%s)",
            protocolConfig.getPort(),
            protocolConfig.getProtocolName(),
            activeAdapter.description());

        activitySource.emitActivity("PROTOCOLO", message);
    }

    public void stopListening() {
        if (!isListening()) {
            return;
        }

        activeAdapter.stop();
        String message = String.format("Servidor dejo de escuchar en puerto %d (%s)",
            protocolConfig.getPort(),
            protocolConfig.getProtocolName());
        activeAdapter = null;

        activitySource.emitActivity("PROTOCOLO", message);
    }

    public CommunicationProtocol getProtocol() {
        return protocolConfig.getProtocol();
    }

    public int getPort() {
        return protocolConfig.getPort();
    }

    public boolean isListening() {
        return activeAdapter != null && activeAdapter.isListening();
    }

    public String getServerInfo() {
        String description = protocolConfig.getProtocolDescription();
        if (activeAdapter != null) {
            description = activeAdapter.description();
        }
        return String.format("%s:%d (%s)",
            protocolConfig.getProtocolName(),
            protocolConfig.getPort(),
            description);
    }
}


