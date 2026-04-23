package com.app.server.net;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandDispatcherTest {

    @Test
    void creaRespuestaOk() {
        Mensaje resp = Mensaje.respuestaOk("hash", "abc123");
        assertEquals(Comando.RESPUESTA, resp.getComando());
        assertEquals("abc123", resp.getString("hash"));
    }

    @Test
    void creaRespuestaError() {
        Mensaje resp = Mensaje.error("Error de prueba");
        assertEquals(Comando.ERROR, resp.getComando());
        assertTrue(resp.getString("detalle").contains("Error"));
    }

    @Test
    void comandoEnviarArchivoContieneCamposRequeridos() {
        Mensaje msg = new Mensaje(Comando.ENVIAR_ARCHIVO)
                .put("nombre", "test.pdf")
                .put("tamano", 1024L);
        
        assertEquals("test.pdf", msg.getString("nombre"));
        assertEquals(1024L, msg.getLong("tamano"));
    }

    @Test
    void comandoEnviarMensajeContieneCamposRequeridos() {
        Mensaje msg = new Mensaje(Comando.ENVIAR_MENSAJE)
                .put("texto", "Hola mundo");
        
        assertEquals("Hola mundo", msg.getString("texto"));
    }

    @Test
    void comandoDescargarArchivoContieneCamposRequeridos() {
        Mensaje msg = new Mensaje(Comando.DESCARGAR_ARCHIVO)
                .put("documentoId", 42L);
        
        assertEquals(42L, msg.getLong("documentoId"));
    }

    @Test
    void comandoDescargarHashContieneCamposRequeridos() {
        Mensaje msg = new Mensaje(Comando.DESCARGAR_HASH)
                .put("documentoId", 99L);
        
        assertEquals(99L, msg.getLong("documentoId"));
    }

    @Test
    void respuestaContieneCamposObligatorios() {
        Mensaje resp = new Mensaje(Comando.RESPUESTA)
                .put("exito", true)
                .put("detalle", "Operación completada");
        
        assertTrue(resp.getBoolean("exito"));
        assertEquals("Operación completada", resp.getString("detalle"));
    }

    @Test
    void sesionInfoMensajeValido() {
        Mensaje msg = new Mensaje(Comando.SESION_INFO)
                .put("status", "CONECTADO")
                .put("protocolo", "TCP");
        
        assertEquals(Comando.SESION_INFO, msg.getComando());
        assertEquals("CONECTADO", msg.getString("status"));
        assertEquals("TCP", msg.getString("protocolo"));
    }

    @Test
    void chatMensajeValido() {
        Mensaje msg = new Mensaje(Comando.CHAT_MENSAJE)
                .put("texto", "Hola")
                .put("desde", "usuario1");
        
        assertEquals(Comando.CHAT_MENSAJE, msg.getComando());
        assertEquals("Hola", msg.getString("texto"));
        assertEquals("usuario1", msg.getString("desde"));
    }

    @Test
    void comandoListarClientesValido() {
        Mensaje msg = new Mensaje(Comando.LISTAR_CLIENTES);
        assertEquals(Comando.LISTAR_CLIENTES, msg.getComando());
    }

    @Test
    void comandoListarDocumentosValido() {
        Mensaje msg = new Mensaje(Comando.LISTAR_DOCUMENTOS);
        assertEquals(Comando.LISTAR_DOCUMENTOS, msg.getComando());
    }
}
