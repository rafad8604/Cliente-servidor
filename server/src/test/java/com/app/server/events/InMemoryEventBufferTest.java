package com.app.server.events;

import com.app.server.net.ClientContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryEventBufferTest {

    @Test
    void guardaEventosEnOrden() {
        InMemoryEventBuffer buf = new InMemoryEventBuffer(10);
        buf.onEvent(new ServerEvent(ServerEventType.TCP_CONEXION_ABIERTA, "n1", "uno", null));
        buf.onEvent(new ServerEvent(ServerEventType.TCP_CONEXION_ABIERTA, "n2", "dos", null));
        buf.onEvent(new ServerEvent(ServerEventType.TCP_CONEXION_ABIERTA, "n3", "tres", null));

        List<ServerEvent> snap = buf.snapshot(10);
        // Mas recientes primero
        assertEquals("tres", snap.get(0).getDetalle());
        assertEquals("dos", snap.get(1).getDetalle());
        assertEquals("uno", snap.get(2).getDetalle());
    }

    @Test
    void descartaEventosViejosAlSuperarCapacidad() {
        InMemoryEventBuffer buf = new InMemoryEventBuffer(3);
        for (int i = 0; i < 10; i++) {
            buf.onEvent(new ServerEvent(ServerEventType.MENSAJE_RECIBIDO, "n", "msg-" + i, null));
        }
        assertEquals(3, buf.size());
        List<ServerEvent> snap = buf.snapshot(10);
        assertEquals("msg-9", snap.get(0).getDetalle());
        assertEquals("msg-8", snap.get(1).getDetalle());
        assertEquals("msg-7", snap.get(2).getDetalle());
    }

    @Test
    void limitRecortaResultado() {
        InMemoryEventBuffer buf = new InMemoryEventBuffer(50);
        for (int i = 0; i < 20; i++) {
            buf.onEvent(new ServerEvent(ServerEventType.MENSAJE_RECIBIDO, "n", "e-" + i, null));
        }
        assertEquals(5, buf.snapshot(5).size());
    }

    @Test
    void preservaContextoDelCliente() {
        InMemoryEventBuffer buf = new InMemoryEventBuffer(10);
        ClientContext ctx = new ClientContext("10.0.0.1", 1234, "TCP");
        buf.onEvent(new ServerEvent(ServerEventType.TCP_CONEXION_ABIERTA, "n", "conn", ctx));
        assertEquals(ctx, buf.snapshot(1).get(0).getClientContext());
    }
}
