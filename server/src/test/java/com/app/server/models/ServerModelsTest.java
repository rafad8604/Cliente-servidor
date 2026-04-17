package com.app.server.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ServerModelsTest {

    @Test
    void documentoConstructorYSetters() {
        Documento d = new Documento("doc.pdf", "pdf", 4096L, "/tmp/doc.pdf", "abc", "127.0.0.1", Documento.Tipo.ARCHIVO);
        assertEquals("doc.pdf", d.getNombre());
        assertEquals("pdf", d.getExtension());
        assertEquals(4096L, d.getTamano());
        assertEquals("/tmp/doc.pdf", d.getRutaLocalOriginal());
        assertEquals("abc", d.getHashSha256());
        assertEquals("127.0.0.1", d.getIpPropietario());
        assertEquals(Documento.Tipo.ARCHIVO, d.getTipo());
        assertNotNull(d.getFechaCreacion());

        LocalDateTime now = LocalDateTime.now();
        d.setId(10L);
        d.setFechaCreacion(now);
        assertEquals(10L, d.getId());
        assertEquals(now, d.getFechaCreacion());
        assertTrue(d.toString().contains("Documento"));
    }

    @Test
    void clienteConectadoConstructorYSetters() {
        ClienteConectado c = new ClienteConectado("10.0.0.5", 9000, "TCP");
        assertEquals("10.0.0.5", c.getIp());
        assertEquals(9000, c.getPuerto());
        assertEquals("TCP", c.getProtocolo());
        assertNotNull(c.getFechaInicio());

        LocalDateTime now = LocalDateTime.now();
        c.setFechaInicio(now);
        assertEquals(now, c.getFechaInicio());
        assertTrue(c.toString().contains("ClienteConectado"));
    }

    @Test
    void documentoChunkConstructorYSetters() {
        byte[] data = new byte[]{1, 2, 3};
        DocumentoChunk chunk = new DocumentoChunk(7L, 0, data);
        assertEquals(7L, chunk.getDocumentoId());
        assertEquals(0, chunk.getChunkIndex());
        assertArrayEquals(data, chunk.getDatosEncriptados());

        chunk.setId(99L);
        assertEquals(99L, chunk.getId());
        assertTrue(chunk.toString().contains("DocumentoChunk"));
    }

    @Test
    void logConstructorYSetters() {
        Log log = new Log("CONEXION", "127.0.0.1", "detalle");
        assertEquals("CONEXION", log.getAccion());
        assertEquals("127.0.0.1", log.getIpOrigen());
        assertEquals("detalle", log.getDetalles());
        assertNotNull(log.getFechaHora());

        log.setId(5L);
        assertEquals(5L, log.getId());
        assertTrue(log.toString().contains("Log"));
    }
}
