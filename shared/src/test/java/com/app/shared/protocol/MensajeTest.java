package com.app.shared.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MensajeTest {

    @Test
    void serializaYDeserializaConTiposBasicos() {
        Mensaje original = new Mensaje(Comando.ENVIAR_MENSAJE)
                .put("texto", "hola")
                .put("tamano", 123L)
                .put("activo", true);

        String json = original.toJson();
        Mensaje reconstruido = Mensaje.fromJson(json);

        assertEquals(Comando.ENVIAR_MENSAJE, reconstruido.getComando());
        assertEquals("hola", reconstruido.getString("texto"));
        assertEquals(123L, reconstruido.getLong("tamano"));
        assertTrue(reconstruido.getBoolean("activo"));
        assertNotNull(reconstruido.getTimestamp());
    }

    @Test
    void helpersGeneranMensajesEsperados() {
        Mensaje ok = Mensaje.respuestaOk("hash", "abc123");
        assertEquals(Comando.RESPUESTA, ok.getComando());
        assertEquals("OK", ok.getString("status"));
        assertEquals("abc123", ok.getString("hash"));

        Mensaje err = Mensaje.error("fallo");
        assertEquals(Comando.ERROR, err.getComando());
        assertEquals("ERROR", err.getString("status"));
        assertEquals("fallo", err.getString("detalle"));
    }
}
