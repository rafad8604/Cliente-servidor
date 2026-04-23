package com.app.server.net;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.pool.ResourcePool;
import com.app.server.pool.SemaphoreResourcePool;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object Pool de clientes.
 *
 * Sigue el patrón Adapter: delega el control de capacidad/concurrencia a un
 * {@link ResourcePool}. El resto del servidor habla con esta clase y nunca
 * directamente con el {@link java.util.concurrent.Semaphore} subyacente.
 *
 * La API pública (tryAcquire / acquire / release / getActiveCount /
 * getMaxClients / registerHandler / unregisterHandler / shutdownAll /
 * getActiveHandlers) se mantiene intacta para no romper tests ni otros
 * consumidores.
 */
public class ClientPool {

    private final ResourcePool resourcePool;
    private final Set<ClientHandler> activeHandlers;
    private final ServerEventBus eventBus;

    public ClientPool(int maxClients) {
        this(new SemaphoreResourcePool("default-pool", maxClients), null);
    }

    public ClientPool(ResourcePool resourcePool, ServerEventBus eventBus) {
        this.resourcePool = resourcePool;
        this.activeHandlers = ConcurrentHashMap.newKeySet();
        this.eventBus = eventBus;
    }

    public boolean acquire() throws InterruptedException {
        boolean ok = resourcePool.acquire();
        if (ok && eventBus != null) {
            eventBus.publish(ServerEventType.POOL_ADQUIRIDO, resourcePool.getName(),
                    "active=" + getActiveCount() + "/" + getMaxClients());
        }
        return ok;
    }

    public boolean tryAcquire() {
        boolean ok = resourcePool.tryAcquire();
        if (ok && eventBus != null) {
            eventBus.publish(ServerEventType.POOL_ADQUIRIDO, resourcePool.getName(),
                    "active=" + getActiveCount() + "/" + getMaxClients());
        }
        return ok;
    }

    public void release() {
        resourcePool.release();
        if (eventBus != null) {
            eventBus.publish(ServerEventType.POOL_LIBERADO, resourcePool.getName(),
                    "active=" + getActiveCount() + "/" + getMaxClients());
        }
    }

    public void registerHandler(ClientHandler handler) {
        activeHandlers.add(handler);
    }

    public void unregisterHandler(ClientHandler handler) {
        activeHandlers.remove(handler);
    }

    public int getActiveCount() {
        return resourcePool.getActiveCount();
    }

    public int getMaxClients() {
        return resourcePool.getCapacity();
    }

    public String getName() {
        return resourcePool.getName();
    }

    public Set<ClientHandler> getActiveHandlers() {
        return Collections.unmodifiableSet(activeHandlers);
    }

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
