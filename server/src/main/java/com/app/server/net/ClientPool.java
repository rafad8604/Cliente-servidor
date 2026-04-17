package com.app.server.net;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Patrón Object Pool para limitar el número de clientes concurrentes.
 * Usa un Semaphore para controlar la concurrencia.
 */
public class ClientPool {

    private final int maxClients;
    private final Semaphore semaphore;
    private final Set<ClientHandler> activeHandlers;

    public ClientPool(int maxClients) {
        this.maxClients = maxClients;
        this.semaphore = new Semaphore(maxClients, true); // fair queue
        this.activeHandlers = ConcurrentHashMap.newKeySet();
    }

    /**
     * Intenta adquirir un slot en el pool. Bloquea hasta que haya uno disponible.
     *
     * @return true si se obtuvo el slot
     * @throws InterruptedException si el hilo fue interrumpido mientras esperaba
     */
    public boolean acquire() throws InterruptedException {
        semaphore.acquire();
        return true;
    }

    /**
     * Intenta adquirir un slot sin bloquear.
     *
     * @return true si se obtuvo el slot, false si el pool está lleno
     */
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * Libera un slot en el pool.
     */
    public void release() {
        semaphore.release();
    }

    /**
     * Registra un handler activo.
     */
    public void registerHandler(ClientHandler handler) {
        activeHandlers.add(handler);
    }

    /**
     * Elimina un handler del registro.
     */
    public void unregisterHandler(ClientHandler handler) {
        activeHandlers.remove(handler);
    }

    /**
     * Obtiene el número de clientes actualmente conectados.
     */
    public int getActiveCount() {
        return maxClients - semaphore.availablePermits();
    }

    /**
     * Obtiene el número máximo de clientes permitidos.
     */
    public int getMaxClients() {
        return maxClients;
    }

    /**
     * Obtiene los handlers activos (solo lectura).
     */
    public Set<ClientHandler> getActiveHandlers() {
        return Collections.unmodifiableSet(activeHandlers);
    }

    /**
     * Cierra todos los handlers activos (para shutdown).
     */
    public void shutdownAll() {
        for (ClientHandler handler : activeHandlers) {
            try {
                handler.close();
            } catch (Exception e) {
                System.err.println("[POOL] Error cerrando handler: " + e.getMessage());
            }
        }
        activeHandlers.clear();
    }
}
