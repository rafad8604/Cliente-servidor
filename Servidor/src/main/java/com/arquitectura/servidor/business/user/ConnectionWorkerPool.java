package com.arquitectura.servidor.business.user;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionWorkerPool implements ObjectPool<PooledConnectionWorker> {

    private final int maxSize;
    private final Queue<PooledConnectionWorker> availableWorkers = new ConcurrentLinkedQueue<>();
    private final Set<PooledConnectionWorker> inUseWorkers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger createdWorkers = new AtomicInteger();

    public ConnectionWorkerPool(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize debe ser mayor a 0");
        }
        this.maxSize = maxSize;
    }

    @Override
    public synchronized PooledConnectionWorker borrowObject() {
        PooledConnectionWorker worker = availableWorkers.poll();
        if (worker != null) {
            inUseWorkers.add(worker);
            return worker;
        }

        if (createdWorkers.get() < maxSize) {
            PooledConnectionWorker newWorker = new PooledConnectionWorker();
            createdWorkers.incrementAndGet();
            inUseWorkers.add(newWorker);
            return newWorker;
        }

        return null;
    }

    @Override
    public synchronized void returnObject(PooledConnectionWorker object) {
        if (object == null) {
            return;
        }
        if (inUseWorkers.remove(object)) {
            availableWorkers.offer(object);
        }
    }

    @Override
    public int availableCount() {
        return availableWorkers.size();
    }

    @Override
    public int inUseCount() {
        return inUseWorkers.size();
    }
}

