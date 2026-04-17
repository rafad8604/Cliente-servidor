package com.app.client.net;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkClientTcpTest {

    @Test
    void uploadArchivoTcpCompleto() throws Exception {
        byte[] contenido = "contenido archivo subida".getBytes(StandardCharsets.UTF_8);
        File origen = File.createTempFile("upload-origen", ".txt");
        Files.write(origen.toPath(), contenido);

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();

                    enviarLinea(out, new Mensaje(Comando.SESION_INFO).put("status", "CONECTADO").toJson());

                    String headerLine = leerLinea(in);
                    Mensaje header = Mensaje.fromJson(headerLine);
                    assertEquals(Comando.ENVIAR_ARCHIVO, header.getComando());
                    assertEquals(origen.getName(), header.getString("nombre"));

                    long tamano = header.getLong("tamano");
                    byte[] recibido = in.readNBytes((int) tamano);
                    assertEquals(tamano, recibido.length);
                    assertArrayEquals(contenido, recibido);

                    enviarLinea(out, Mensaje.respuestaOk("hash", "hash-ok").toJson());
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

            NetworkClient client = new NetworkClient("127.0.0.1", port, NetworkClient.Protocolo.TCP);
            try {
                client.conectar();
                Mensaje resp = client.enviarArchivo(origen, null).get(5, TimeUnit.SECONDS);
                assertEquals("hash-ok", resp.getString("hash"));
            } finally {
                client.close();
            }

            serverTask.get(5, TimeUnit.SECONDS);
        } finally {
            origen.delete();
        }
    }

    @Test
    void descargaArchivoTcpCompletaConservaBytes() throws Exception {
        byte[] payload = new byte[]{37, 80, 68, 70, 45, 0, 1, 2, 3, 10, 13, -1, 120}; // Bytes tipo PDF/binario
        File destino = File.createTempFile("download-destino", ".bin");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();

                    enviarLinea(out, new Mensaje(Comando.SESION_INFO).put("status", "CONECTADO").toJson());

                    String cmdLine = leerLinea(in);
                    Mensaje cmd = Mensaje.fromJson(cmdLine);
                    assertEquals(Comando.DESCARGAR_ARCHIVO, cmd.getComando());

                    enviarLinea(out, new Mensaje(Comando.RESPUESTA)
                            .put("status", "OK")
                            .put("tamano", payload.length)
                            .put("nombre", "archivo.bin")
                            .toJson());
                    out.write(payload);
                    out.flush();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

            NetworkClient client = new NetworkClient("127.0.0.1", port, NetworkClient.Protocolo.TCP);
            try {
                client.conectar();
                client.descargarArchivo(1L, destino, null);
            } finally {
                client.close();
            }

            byte[] descargado = Files.readAllBytes(destino.toPath());
            assertArrayEquals(payload, descargado);
            serverTask.get(5, TimeUnit.SECONDS);
        } finally {
            destino.delete();
        }
    }

    @Test
    void descargaArchivoTcpIncompletaFalla() throws Exception {
        byte[] payload = "abc".getBytes(StandardCharsets.UTF_8);
        File destino = File.createTempFile("download-incompleto", ".txt");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();

                    enviarLinea(out, new Mensaje(Comando.SESION_INFO).put("status", "CONECTADO").toJson());
                    leerLinea(in); // DESCARGAR_ARCHIVO

                    enviarLinea(out, new Mensaje(Comando.RESPUESTA)
                            .put("status", "OK")
                            .put("tamano", payload.length + 5)
                            .put("nombre", "incompleto.txt")
                            .toJson());
                    out.write(payload);
                    out.flush();
                    s.shutdownOutput();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

            NetworkClient client = new NetworkClient("127.0.0.1", port, NetworkClient.Protocolo.TCP);
            try {
                client.conectar();
                Exception ex = assertThrows(Exception.class, () -> client.descargarArchivo(1L, destino, null));
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                assertTrue(msg.contains("incompleta") || msg.contains("incompleto") || msg.contains("faltan"));
            } finally {
                client.close();
            }

            serverTask.get(5, TimeUnit.SECONDS);
        } finally {
            destino.delete();
        }
    }

    @Test
    void descargaEncriptadoTcpLeeBloquesConFin() throws Exception {
        byte[] payload = "ENCRYPTED-BLOCKS".getBytes(StandardCharsets.UTF_8);
        File destino = File.createTempFile("download-enc", ".enc");

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();
                    DataOutputStream dos = new DataOutputStream(out);

                    enviarLinea(out, new Mensaje(Comando.SESION_INFO).put("status", "CONECTADO").toJson());

                    String cmdLine = leerLinea(in);
                    Mensaje cmd = Mensaje.fromJson(cmdLine);
                    assertEquals(Comando.DESCARGAR_ENCRIPTADO, cmd.getComando());

                    enviarLinea(out, new Mensaje(Comando.RESPUESTA)
                            .put("status", "OK")
                            .put("nombre", "archivo.enc")
                            .put("tipoDescarga", "ENCRIPTADO")
                            .toJson());

                    dos.writeInt(payload.length);
                    dos.write(payload);
                    dos.writeInt(0);
                    dos.flush();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

            NetworkClient client = new NetworkClient("127.0.0.1", port, NetworkClient.Protocolo.TCP);
            try {
                client.conectar();
                client.descargarEncriptado(1L, destino, null);
            } finally {
                client.close();
            }

            byte[] descargado = Files.readAllBytes(destino.toPath());
            assertTrue(Arrays.equals(payload, descargado));
            serverTask.get(5, TimeUnit.SECONDS);
        } finally {
            destino.delete();
        }
    }

    private static void enviarLinea(OutputStream out, String json) throws IOException {
        out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String leerLinea(InputStream in) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(128);
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (lineBuffer.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            if (b != '\r') lineBuffer.write(b);
        }
        return lineBuffer.toString(StandardCharsets.UTF_8);
    }
}
