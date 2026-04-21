package com.app.server.pool;

/**
 * Abstracción del Object Pool.
 *
 * Oculta la implementación concreta (Semaphore, JDBC, etc.) detrás de una
 * interfaz estable para el resto del servidor. Las capas superiores sólo
 * conocen las operaciones: adquirir / liberar / consultar capacidad.
 */
public interface ResourcePool {

    /**
     * Intenta adquirir un slot sin bloquear.
     *
     * @return true si se obtuvo el slot, false si el pool está lleno
     */
    boolean tryAcquire();

    /**
     * Adquiere un slot bloqueando hasta que haya disponibilidad.
     *
     * @return true cuando se obtiene el slot
     * @throws InterruptedException si el hilo es interrumpido en la espera
     */
    boolean acquire() throws InterruptedException;

    /**
     * Libera un slot previamente adquirido.
     */
    void release();

    /**
     * Cantidad de slots ocupados actualmente.
     */
    int getActiveCount();

    /**
     * Capacidad máxima del pool.
     */
    int getCapacity();

    /**
     * Nombre lógico del pool (útil para logs y eventos).
     */
    String getName();
}
