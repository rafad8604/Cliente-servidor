package com.arquitectura.servidor.business.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionWorkerPoolTest {

    @Test
    void shouldBorrowUntilMaxSizeAndThenReturnNull() {
        ConnectionWorkerPool pool = new ConnectionWorkerPool(2);

        PooledConnectionWorker worker1 = pool.borrowObject();
        PooledConnectionWorker worker2 = pool.borrowObject();
        PooledConnectionWorker worker3 = pool.borrowObject();

        assertNotNull(worker1);
        assertNotNull(worker2);
        assertNull(worker3);
        assertEquals(0, pool.availableCount());
        assertEquals(2, pool.inUseCount());
    }

    @Test
    void shouldReturnWorkerToAvailablePool() {
        ConnectionWorkerPool pool = new ConnectionWorkerPool(1);

        PooledConnectionWorker worker = pool.borrowObject();
        assertNotNull(worker);

        pool.returnObject(worker);

        assertEquals(1, pool.availableCount());
        assertEquals(0, pool.inUseCount());
    }

    @Test
    void shouldReuseReturnedWorker() {
        ConnectionWorkerPool pool = new ConnectionWorkerPool(1);

        PooledConnectionWorker worker1 = pool.borrowObject();
        pool.returnObject(worker1);
        PooledConnectionWorker worker2 = pool.borrowObject();

        assertNotNull(worker2);
        assertEquals(worker1, worker2);
    }

    @Test
    void shouldFailOnInvalidPoolSize() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionWorkerPool(0));
    }
}

