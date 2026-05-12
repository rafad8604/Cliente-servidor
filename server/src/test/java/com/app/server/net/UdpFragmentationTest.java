package com.app.server.net;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UdpFragmentationTest {

    private DatagramSocket receptor;
    private DatagramSocket emisor;

    @BeforeEach
    void setup() throws Exception {
        receptor = new DatagramSocket(0);
        emisor = new DatagramSocket();
    }

    @AfterEach
    void tearDown() {
        if (receptor != null && !receptor.isClosed()) receptor.close();
        if (emisor != null && !emisor.isClosed()) emisor.close();
    }

    @Test
    void mensajePequenoEnviaUnSoloPaqueteControl() throws Exception {
        UdpClientChannel channel = new UdpClientChannel(emisor,
                InetAddress.getLoopbackAddress(), receptor.getLocalPort(), 42);

        channel.sendMensaje(new Mensaje(Comando.PEER_PING));

        byte[] buf = new byte[16000];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        receptor.setSoTimeout(500);
        receptor.receive(dp);
        assertEquals(UdpClientChannel.TIPO_CONTROL, dp.getData()[0] & 0xFF);

        // No deberia llegar mas paquetes.
        receptor.setSoTimeout(100);
        assertThrows(SocketTimeoutException.class, () -> {
            DatagramPacket otro = new DatagramPacket(buf, buf.length);
            receptor.receive(otro);
        });
    }

    @Test
    void mensajeGrandeSeFragmentaConEnd() throws Exception {
        UdpClientChannel channel = new UdpClientChannel(emisor,
                InetAddress.getLoopbackAddress(), receptor.getLocalPort(), 42);

        // Forzar JSON > MAX_CONTROL_PAYLOAD (8000 - 9 = 7991 bytes).
        StringBuilder sb = new StringBuilder(20_000);
        for (int i = 0; i < 20_000; i++) sb.append('A');
        Mensaje grande = new Mensaje(Comando.RESPUESTA).put("payload", sb.toString());

        channel.sendMensaje(grande);

        receptor.setSoTimeout(500);
        List<Integer> tipos = new ArrayList<>();
        while (true) {
            byte[] buf = new byte[16000];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                receptor.receive(dp);
                tipos.add(dp.getData()[0] & 0xFF);
            } catch (SocketTimeoutException e) {
                break;
            }
        }

        assertTrue(tipos.size() >= 2, "Debe llegar al menos un FRAG y un END");
        assertEquals(UdpClientChannel.TIPO_CONTROL_END, tipos.get(tipos.size() - 1).intValue());
        for (int i = 0; i < tipos.size() - 1; i++) {
            assertEquals(UdpClientChannel.TIPO_CONTROL_FRAG, tipos.get(i).intValue());
        }
    }

    @Test
    void seqNumIncreaSecuencialmenteEnFragmentos() throws Exception {
        UdpClientChannel channel = new UdpClientChannel(emisor,
                InetAddress.getLoopbackAddress(), receptor.getLocalPort(), 42);

        StringBuilder sb = new StringBuilder(20_000);
        for (int i = 0; i < 20_000; i++) sb.append('B');
        channel.sendMensaje(new Mensaje(Comando.RESPUESTA).put("payload", sb.toString()));

        receptor.setSoTimeout(500);
        List<Integer> seqs = new ArrayList<>();
        while (true) {
            byte[] buf = new byte[16000];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                receptor.receive(dp);
                int seq = java.nio.ByteBuffer.wrap(dp.getData(), 5, 4).getInt();
                seqs.add(seq);
            } catch (SocketTimeoutException e) {
                break;
            }
        }

        for (int i = 0; i < seqs.size(); i++) {
            assertEquals(i, seqs.get(i).intValue(), "seqNum debe ser secuencial");
        }
    }
}
