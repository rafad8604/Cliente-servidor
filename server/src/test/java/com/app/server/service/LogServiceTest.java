package com.app.server.service;

import com.app.server.models.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para LogService.
 * 
 * NOTA: Deshabilitados por defecto ya que requieren conexión a BD MySQL.
 * Para habilitarlos: asegúrate que Docker MySQL esté corriendo y ejecuta.
 * Runtimes esperado: ~2-3 segundos por test.
 * 
 * Ejecutar solo estos tests (requiere MySQL):
 *   docker compose up -d
 *   mvn test -Dtest=LogServiceTest
 */
@Disabled("Requiere BD MySQL - ejecutar manualmente")
class LogServiceTest {

    private LogService logService;

    @BeforeEach
    void setup() {
        logService = new LogService();
    }

    @Test
    void registraLogConexion() {
        assertDoesNotThrow(() -> {
            logService.logConexion("192.168.1.100", "TCP");
        });
    }

    @Test
    void registraLogDesconexion() {
        assertDoesNotThrow(() -> {
            logService.logDesconexion("192.168.1.100");
        });
    }

    @Test
    void registraLogArchivoRecibido() {
        assertDoesNotThrow(() -> {
            logService.logArchivoRecibido("192.168.1.100", "documento.pdf", 4096L);
        });
    }

    @Test
    void registraLogGeneral() {
        assertDoesNotThrow(() -> {
            logService.registrar("OPERACION", "127.0.0.1", "Operación completada");
        });
    }

    @Test
    void logConexionTieneValoresValidos() throws Exception {
        Log log = new Log("CONEXION", "127.0.0.1", "Cliente conectado vía TCP");
        
        assertNotNull(log);
        assertEquals("CONEXION", log.getAccion());
        assertEquals("127.0.0.1", log.getIpOrigen());
        assertEquals("Cliente conectado vía TCP", log.getDetalles());
        assertNotNull(log.getFechaHora());
    }

    @Test
    void logDesconexionTieneValoresValidos() throws Exception {
        Log log = new Log("DESCONEXION", "192.168.1.50", "Cliente desconectado");
        
        assertEquals("DESCONEXION", log.getAccion());
        assertEquals("192.168.1.50", log.getIpOrigen());
    }

    @Test
    void logErrorTieneValoresValidos() throws Exception {
        Log log = new Log("ERROR", "10.0.0.1", "Timeout en transacción");
        
        assertEquals("ERROR", log.getAccion());
        assertEquals("10.0.0.1", log.getIpOrigen());
        assertTrue(log.getDetalles().length() > 0);
    }

    @Test
    void logManejaIpsVarias() {
        String[] ips = {
            "127.0.0.1",
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "localhost",
            "::1"
        };

        for (String ip : ips) {
            Log log = new Log("CONEXION", ip, "Test IP: " + ip);
            assertNotNull(log);
            assertEquals(ip, log.getIpOrigen());
        }
    }

    @Test
    void logAccionesVariadas() {
        String[] acciones = {
            "CONEXION",
            "DESCONEXION",
            "ARCHIVO_RECIBIDO",
            "DESCARGA_ARCHIVO",
            "ERROR",
            "CAMBIO_ARCHIVO",
            "VALIDACION"
        };

        for (String accion : acciones) {
            Log log = new Log(accion, "127.0.0.1", "Acción: " + accion);
            assertNotNull(log);
            assertEquals(accion, log.getAccion());
        }
    }

    @Test
    void logDetallesLargos() {
        String detalleLargo = "Lorem ipsum ".repeat(50);
        Log log = new Log("OPERACION", "127.0.0.1", detalleLargo);
        
        assertEquals(detalleLargo, log.getDetalles());
        assertTrue(log.getDetalles().length() > 100);
    }

    @Test
    void registraMultiplesOperacionesConIpsDiferentes() {
        String[] ips = {"127.0.0.1", "192.168.1.1", "10.0.0.1"};
        String[] acciones = {"CONEXION", "ARCHIVO_RECIBIDO", "DESCONEXION"};
        
        for (String ip : ips) {
            for (String accion : acciones) {
                assertDoesNotThrow(() -> {
                    logService.registrar(accion, ip, "Test: " + accion + " desde " + ip);
                });
            }
        }
    }
}
