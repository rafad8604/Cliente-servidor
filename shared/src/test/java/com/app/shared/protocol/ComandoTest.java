package com.app.shared.protocol;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ComandoTest {

    @Test
    void contieneComandosEsperados() {
        Set<String> nombres = Arrays.stream(Comando.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertTrue(nombres.contains("ENVIAR_ARCHIVO"));
        assertTrue(nombres.contains("ENVIAR_MENSAJE"));
        assertTrue(nombres.contains("LISTAR_DOCUMENTOS"));
        assertTrue(nombres.contains("DESCARGAR_ARCHIVO"));
        assertTrue(nombres.contains("DESCARGAR_HASH"));
        assertTrue(nombres.contains("DESCARGAR_ENCRIPTADO"));
        assertTrue(nombres.contains("LISTAR_CLIENTES"));
        assertTrue(nombres.contains("RESPUESTA"));
        assertTrue(nombres.contains("ERROR"));
        assertTrue(nombres.contains("CHAT_MENSAJE"));
        assertTrue(nombres.contains("SESION_INFO"));

        assertEquals(11, nombres.size());
    }
}
