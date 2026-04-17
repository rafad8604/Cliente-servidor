package com.app.server.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ClientPoolTest {

    @Test
    void tryAcquireYReleaseControlanCapacidad() {
        ClientPool pool = new ClientPool(2);

        assertTrue(pool.tryAcquire());
        assertTrue(pool.tryAcquire());
        assertFalse(pool.tryAcquire());
        assertEquals(2, pool.getActiveCount());

        pool.release();
        assertEquals(1, pool.getActiveCount());
        assertTrue(pool.tryAcquire());
        assertEquals(2, pool.getActiveCount());
    }

    @Test
    void registerUnregisterYShutdownAll() throws Exception {
        ClientPool pool = new ClientPool(1);
        Socket[] sockets = createConnectedSockets();

        ClientHandler handler = new ClientHandler(sockets[0], pool, null, null);
        pool.registerHandler(handler);
        assertEquals(1, pool.getActiveHandlers().size());

        pool.unregisterHandler(handler);
        assertEquals(0, pool.getActiveHandlers().size());

        pool.registerHandler(handler);
        pool.shutdownAll();
        assertEquals(0, pool.getActiveHandlers().size());

        assertTrue(sockets[0].isClosed());
        sockets[1].close();
    }

    @Test
    void acquireBloqueaHastaRelease() throws Exception {
        ClientPool pool = new ClientPool(1);
        assertTrue(pool.tryAcquire());

        CompletableFuture<Boolean> waitedAcquire = CompletableFuture.supplyAsync(() -> {
            try {
                return pool.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });

        assertThrows(Exception.class, () -> waitedAcquire.get(200, TimeUnit.MILLISECONDS));

        pool.release();
        assertTrue(waitedAcquire.get(2, TimeUnit.SECONDS));
    }

    private Socket[] createConnectedSockets() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
                try {
                    return server.accept();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            Socket client = new Socket("127.0.0.1", port);
            Socket serverSide = accepted.get(2, TimeUnit.SECONDS);
            return new Socket[]{serverSide, client};
        }
    }
}
