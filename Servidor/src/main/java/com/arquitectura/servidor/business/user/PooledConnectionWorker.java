package com.arquitectura.servidor.business.user;

import com.arquitectura.servidor.business.activity.ServerActivitySource;

public class PooledConnectionWorker implements Runnable {

    private volatile boolean active;
    private UserConnection connection;
    private ServerActivitySource activitySource;

    public void assign(UserConnection connection, ServerActivitySource activitySource) {
        this.connection = connection;
        this.activitySource = activitySource;
        this.active = true;
    }

    public void stopProcessing() {
        active = false;
    }

    public void reset() {
        active = false;
        connection = null;
        activitySource = null;
    }

    @Override
    public void run() {
        if (connection == null || activitySource == null) {
            return;
        }

        String threadName = Thread.currentThread().getName();
        activitySource.emitActivity("CONEXION_HILO", "Hilo " + threadName + " procesando a '" + connection.username() + "'");

        try {
            while (active && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (connection != null && activitySource != null) {
                activitySource.emitActivity("CONEXION_HILO", "Hilo " + threadName + " finalizado para '" + connection.username() + "'");
            }
        }
    }
}

