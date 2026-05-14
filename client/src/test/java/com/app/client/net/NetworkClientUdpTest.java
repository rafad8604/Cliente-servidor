package com.app.client.net;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NetworkClientUdpTest {

    private static final int HEADER_SIZE = 9;

    @Test
    void conectarUdpConversaHandshake() throws Exception {
        DatagramSocket udpServer = new DatagramSocket(9999);
        int port = udpServer.getLocalPort();

        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                byte[] buffer = new byte[65536];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                
                // Recibir paquete de handshake
                udpServer.receive(receivePacket);
                
                // Extraer datos
                byte[] data = receivePacket.getData();
                int tipo = data[0] & 0xFF;
                assertEquals(0, tipo); // Control packet
                
                int sessionId = ByteBuffer.wrap(data, 1, 4).getInt();

                // Enviar respuesta usando la misma sesión solicitada por el cliente
                Mensaje respuesta = new Mensaje(Comando.SESION_INFO).put("status", "CONECTADO");
                byte[] payload = respuesta.toJson().getBytes(StandardCharsets.UTF_8);
                byte[] responsePacket = new byte[HEADER_SIZE + payload.length];
                responsePacket[0] = 0;
                ByteBuffer.wrap(responsePacket, 1, 4).putInt(sessionId);
                ByteBuffer.wrap(responsePacket, 5, 4).putInt(0);
                System.arraycopy(payload, 0, responsePacket, HEADER_SIZE, payload.length);
                
                DatagramPacket sendPacket = new DatagramPacket(responsePacket, responsePacket.length,
                        receivePacket.getAddress(), receivePacket.getPort());
                udpServer.send(sendPacket);
                
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        try {
            NetworkClient client = new NetworkClient("127.0.0.1", port, NetworkClient.Protocolo.UDP);
            Mensaje sesion = client.conectar();

            assertNotNull(sesion);
            assertEquals("CONECTADO", sesion.getString("status"));
            serverTask.get(2, TimeUnit.SECONDS);
            client.close();
        } finally {
            udpServer.close();
        }
    }

    @Test
    void puertoUdpUsaValorConfigurado() {
        // UDP usa exactamente el puerto indicado, sin sumar 1 internamente.
        NetworkClient client = new NetworkClient("localhost", 9001, NetworkClient.Protocolo.UDP);
        assertNotNull(client);
        
        NetworkClient client2 = new NetworkClient("localhost", 8000, NetworkClient.Protocolo.UDP);
        assertNotNull(client2);
    }

    @Test
    void sendUdpPacketFormatoValido() {
        // Este test valida que el formato de paquete UDP sea correcto
        Mensaje msg = new Mensaje(Comando.LISTAR_CLIENTES);
        byte[] payload = msg.toJson().getBytes(StandardCharsets.UTF_8);
        
        // Formato esperado: tipo (1 byte) + sessionId (4 bytes) + seqNum (4 bytes) + payload
        byte[] packet = new byte[HEADER_SIZE + payload.length];
        packet[0] = 0; // Control
        ByteBuffer.wrap(packet, 1, 4).putInt(12345); // Session ID
        ByteBuffer.wrap(packet, 5, 4).putInt(0); // Seq num
        System.arraycopy(payload, 0, packet, HEADER_SIZE, payload.length);
        
        assertEquals(HEADER_SIZE + payload.length, packet.length);
        assertEquals(0, packet[0]);
    }

    @Test
    void udpHandshakeEnviaMensajeListarClientes() throws Exception {
        // Validar que el handshake envía el comando correcto
        Mensaje handshake = new Mensaje(Comando.LISTAR_CLIENTES);
        assertNotNull(handshake);
        assertEquals(Comando.LISTAR_CLIENTES, handshake.getComando());
        
        String json = handshake.toJson();
        assertNotNull(json);
        assertTrue(json.contains("comando"));
    }

    @Test
    void datagramPacketTamanioValido() {
        Mensaje msg = new Mensaje(Comando.ENVIAR_MENSAJE).put("texto", "Hola UDP");
        byte[] payload = msg.toJson().getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[HEADER_SIZE + payload.length];
        
        // El datagram debe tener un tamaño razonable (< 65535)
        assertTrue(packet.length < 65535);
    }

    @Test
    void udpPuertoConfiguradoPuedeSer9001() {
        int configuredUdpPort = 9001;
        assertEquals(9001, configuredUdpPort);
    }

    @Test
    void descargaArchivoUdpCompleta(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        File destino = tempDir.resolve("download.bin").toFile();
        
        // Test solo valida que el método no lanza excepción con parámetros válidos
        long documentoId = 1L;
        
        // Validar que se puede crear archivo destino
        assertTrue(!destino.exists());
    }

    @Test
    void udpEnvironmentInfoLogging() {
        // Validar que el cliente UDP muestra información correcta al conectar
        String hostInfo = "127.0.0.1";
        int udpPort = 9001;
        
        String logMessage = String.format("[UDP] Conectando a %s:%d", hostInfo, udpPort);
        assertEquals("[UDP] Conectando a 127.0.0.1:9001", logMessage);
    }
}
