package com.app.client.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HistorialDocumentoTest {

    @Test
    void constructorInicializaCamposBasicos() {
        HistorialDocumento h = new HistorialDocumento(
                "archivo.txt",
                1024L,
                "ARCHIVO",
                HistorialDocumento.Direccion.ENVIADO
        );

        assertEquals("archivo.txt", h.getNombre());
        assertEquals(1024L, h.getTamano());
        assertEquals("ARCHIVO", h.getTipo());
        assertEquals(HistorialDocumento.Direccion.ENVIADO, h.getDireccion());
        assertNotNull(h.getFecha());
    }

    @Test
    void gettersYSettersFuncionan() {
        HistorialDocumento h = new HistorialDocumento();
        LocalDateTime now = LocalDateTime.now();

        h.setId(99L);
        h.setNombre("msg");
        h.setTamano(12L);
        h.setTipo("MENSAJE");
        h.setDireccion(HistorialDocumento.Direccion.RECIBIDO);
        h.setFecha(now);

        assertEquals(99L, h.getId());
        assertEquals("msg", h.getNombre());
        assertEquals(12L, h.getTamano());
        assertEquals("MENSAJE", h.getTipo());
        assertEquals(HistorialDocumento.Direccion.RECIBIDO, h.getDireccion());
        assertEquals(now, h.getFecha());
        assertTrue(h.toString().contains("HistorialDocumento"));
    }
}
