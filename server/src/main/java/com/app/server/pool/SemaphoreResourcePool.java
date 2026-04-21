package com.app.server.pool;

import java.util.concurrent.Semaphore;

/**
 * Adapter que implementa {@link ResourcePool} sobre un {@link Semaphore} de
 * Java. El Semaphore queda encapsulado y nunca se expone a capas superiores.
 *
 * Mantiene el comportamiento thread-safe del Object Pool original y preserva
 * el control de concurrencia con un permit por slot.
 */
public class SemaphoreResourcePool implements ResourcePool {

    private final String name;
    private final int capacity;
    private final Semaphore semaphore;

    public SemaphoreResourcePool(String name, int capacity) {
        this(name, capacity, true);
    }

    public SemaphoreResourcePool(String name, int capacity, boolean fair) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity debe ser > 0");
        }
        this.name = name;
        this.capacity = capacity;
        this.semaphore = new Semaphore(capacity, fair);
    }

    @Override
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    @Override
    public boolean acquire() throws InterruptedException {
        semaphore.acquire();
        return true;
    }

    @Override
    public void release() {
        semaphore.release();
    }

    @Override
    public int getActiveCount() {
        return capacity - semaphore.availablePermits();
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public String getName() {
        return name;
    }
}
