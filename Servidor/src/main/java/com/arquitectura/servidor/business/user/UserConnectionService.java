package com.arquitectura.servidor.business.user;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserConnectionService {

    private final Map<String, ConnectionSession> activeSessions = new ConcurrentHashMap<>();
    private final ObjectPool<PooledConnectionWorker> workerPool;
    private final UserConnectionConfig config;
    private final ServerActivitySource activitySource;

    @Autowired
    public UserConnectionService(UserConnectionConfig config, ServerActivitySource activitySource) {
        this(config, activitySource, new ConnectionWorkerPool(config.getMaxUsers()));
    }

    UserConnectionService(UserConnectionConfig config,
                          ServerActivitySource activitySource,
                          ObjectPool<PooledConnectionWorker> workerPool) {
        this.config = config;
        this.activitySource = activitySource;
        this.workerPool = workerPool;
    }

    public UserConnection connect(String username) throws UserConnectionLimitExceededException {
        return connect(username, "127.0.0.1");
    }

    public UserConnection connect(String username, String clientIp) throws UserConnectionLimitExceededException {
        if (activeSessions.size() >= config.getMaxUsers()) {
            String message = String.format("Limite de usuarios alcanzado. Maximo: %d, Actuales: %d",
                    config.getMaxUsers(), activeSessions.size());
            activitySource.emitActivity("CONEXION_RECHAZADA", "Usuario '" + username + "' rechazado: " + message);
            throw new UserConnectionLimitExceededException(message);
        }

        PooledConnectionWorker worker = workerPool.borrowObject();
        if (worker == null) {
            String message = "No hay workers disponibles en el pool para nuevas conexiones";
            activitySource.emitActivity("CONEXION_RECHAZADA", "Usuario '" + username + "' rechazado: " + message);
            throw new UserConnectionLimitExceededException(message);
        }

        UserConnection connection = UserConnection.of(username, clientIp);
        worker.assign(connection, activitySource);

        Thread connectionThread = new Thread(worker, "conn-" + connection.username() + "-" + connection.userId().substring(0, 8));
        connectionThread.setDaemon(true);
        activeSessions.put(connection.userId(), new ConnectionSession(connection, worker, connectionThread));
        connectionThread.start();

        activitySource.emitActivity("CONEXION", String.format("Usuario '%s' conectado desde %s. (%d/%d)",
                username, clientIp, activeSessions.size(), config.getMaxUsers()));
        return connection;
    }

    public void disconnect(String userId) {
        ConnectionSession removed = activeSessions.remove(userId);
        if (removed != null) {
            removed.worker().stopProcessing();
            removed.thread().interrupt();
            waitThreadStop(removed.thread());
            removed.worker().reset();
            workerPool.returnObject(removed.worker());

            activitySource.emitActivity("DESCONEXION", String.format("Usuario '%s' desconectado. (%d/%d)",
                    removed.connection().username(), activeSessions.size(), config.getMaxUsers()));
        }
    }

    public List<UserConnection> getActiveConnections() {
        List<UserConnection> connections = new ArrayList<>();
        for (ConnectionSession session : activeSessions.values()) {
            connections.add(session.connection());
        }
        return List.copyOf(connections);
    }

    public int getCurrentConnectionCount() {
        return activeSessions.size();
    }

    public int getConnectedClientsCount() {
        return getCurrentConnectionCount();
    }

    public List<ConnectedClientInfo> getConnectedClientsInfo() {
        List<ConnectedClientInfo> clientsInfo = new ArrayList<>();
        ZoneId zoneId = ZoneId.systemDefault();
        for (ConnectionSession session : activeSessions.values()) {
            clientsInfo.add(new ConnectedClientInfo(
                session.connection().ipAddress(),
                session.connection().connectedAt().atZone(zoneId).toLocalDate(),
                session.connection().connectedAt().atZone(zoneId).toLocalTime().withNano(0)
            ));
        }
        return List.copyOf(clientsInfo);
    }

    public int getMaxConnections() {
        return config.getMaxUsers();
    }

    public int getAvailableWorkerCount() {
        return workerPool.availableCount();
    }

    public int getInUseWorkerCount() {
        return workerPool.inUseCount();
    }

    public boolean isConnectionThreadRunning(String userId) {
        ConnectionSession session = activeSessions.get(userId);
        return session != null && session.thread().isAlive();
    }

    @PreDestroy
    public void shutdownAllConnections() {
        for (String userId : List.copyOf(activeSessions.keySet())) {
            disconnect(userId);
        }
    }

    private void waitThreadStop(Thread thread) {
        try {
            thread.join(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record ConnectionSession(UserConnection connection, PooledConnectionWorker worker, Thread thread) {
    }
}

