package com.app.server.dao;

import com.app.server.models.ClienteConectado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para ClienteConectadoDAO.
 * 
 * NOTA: Deshabilitados por defecto ya que requieren conexión a BD MySQL.
 * Para habilitarlos: asegúrate que Docker MySQL esté corriendo.
 * Runtimes esperado: ~2-3 segundos por test.
 * 
 * Ejecutar solo estos tests (requiere MySQL):
 *   docker compose up -d
 *   mvn test -Dtest=ClienteConectadoDAOTest
 */
@Disabled("Requiere BD MySQL - ejecutar manualmente")
class ClienteConectadoDAOTest {

    private ClienteConectadoDAO dao;

    @BeforeEach
    void setup() {
        dao = new ClienteConectadoDAO();
    }

    @Test
    void registraClienteConexito() {
        ClienteConectado cliente = new ClienteConectado("192.168.1.100", 9000, "TCP");
        
        assertDoesNotThrow(() -> {
            dao.registrar(cliente);
        });
    }

    @Test
    void construyeClienteConValoresValidos() {
        ClienteConectado cliente = new ClienteConectado("127.0.0.1", 8080, "HTTP");
        
        assertEquals("127.0.0.1", cliente.getIp());
        assertEquals(8080, cliente.getPuerto());
        assertEquals("HTTP", cliente.getProtocolo());
        assertNotNull(cliente.getFechaInicio());
    }

    @Test
    void registraMultiplesProtocolos() {
        ClienteConectado tcp = new ClienteConectado("10.0.0.1", 9000, "TCP");
        ClienteConectado udp = new ClienteConectado("10.0.0.1", 9001, "UDP");
        ClienteConectado http = new ClienteConectado("10.0.0.1", 8080, "HTTP");

        assertDoesNotThrow(() -> {
            dao.registrar(tcp);
            dao.registrar(udp);
            dao.registrar(http);
        });
    }

    @Test
    void registraClientesConIpsDiferentes() {
        String[] ips = {
            "127.0.0.1",
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1"
        };

        for (String ip : ips) {
            ClienteConectado cliente = new ClienteConectado(ip, 9000, "TCP");
            assertDoesNotThrow(() -> {
                dao.registrar(cliente);
            });
        }
    }

    @Test
    void clienteConectadoToString() {
        ClienteConectado cliente = new ClienteConectado("127.0.0.1", 9000, "TCP");
        String str = cliente.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("127.0.0.1") || str.contains("ClienteConectado"));
    }

    @Test
    void clienteConectadoFechaInicio() {
        ClienteConectado cliente = new ClienteConectado("127.0.0.1", 9000, "TCP");
        LocalDateTime fecha = cliente.getFechaInicio();
        
        assertNotNull(fecha);
        assertTrue(fecha.isBefore(LocalDateTime.now()) || fecha.isEqual(LocalDateTime.now()));
    }

    @Test
    void clienteConectadoProtocolos() {
        String[] protocolos = {"TCP", "UDP", "HTTP"};
        
        for (String protocolo : protocolos) {
            ClienteConectado cliente = new ClienteConectado("127.0.0.1", 9000, protocolo);
            assertEquals(protocolo, cliente.getProtocolo());
        }
    }

    @Test
    void clienteConectadoPuertos() {
        int[] puertos = {9000, 9001, 8080, 3000, 5000};
        
        for (int puerto : puertos) {
            ClienteConectado cliente = new ClienteConectado("127.0.0.1", puerto, "TCP");
            assertEquals(puerto, cliente.getPuerto());
        }
    }

    @Test
    void registraClienteHTTP() {
        ClienteConectado cliente = new ClienteConectado("10.20.30.40", 8080, "HTTP");
        
        assertDoesNotThrow(() -> {
            dao.registrar(cliente);
        });
        
        assertEquals("10.20.30.40", cliente.getIp());
        assertEquals(8080, cliente.getPuerto());
        assertEquals("HTTP", cliente.getProtocolo());
    }

    @Test
    void registraClienteUDP() {
        ClienteConectado cliente = new ClienteConectado("192.168.0.1", 9001, "UDP");
        
        assertDoesNotThrow(() -> {
            dao.registrar(cliente);
        });
        
        assertEquals("192.168.0.1", cliente.getIp());
        assertEquals(9001, cliente.getPuerto());
        assertEquals("UDP", cliente.getProtocolo());
    }

    @Test
    void registraClienteTCP() {
        ClienteConectado cliente = new ClienteConectado("172.16.1.1", 9000, "TCP");
        
        assertDoesNotThrow(() -> {
            dao.registrar(cliente);
        });
        
        assertEquals("172.16.1.1", cliente.getIp());
        assertEquals(9000, cliente.getPuerto());
        assertEquals("TCP", cliente.getProtocolo());
    }
}
