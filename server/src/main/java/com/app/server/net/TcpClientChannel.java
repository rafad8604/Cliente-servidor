package com.app.server.net;

import com.app.shared.protocol.Mensaje;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Adapter de {@link ClientChannel} para TCP.
 * Envuelve un {@link Socket} y serializa los envíos (synchronized sobre el
 * OutputStream) para permitir uso concurrente.
 */
public class TcpClientChannel implements ClientChannel {

    private final Socket socket;
    private final ClientContext context;
    private final Object writeLock = new Object();

    public TcpClientChannel(Socket socket) {
        this.socket = socket;
        this.context = new ClientContext(
                socket.getInetAddress().getHostAddress(),
                socket.getPort(),
                "TCP");
    }

    @Override
    public ClientContext getContext() {
        return context;
    }

    /**
     * InputStream crudo del socket.
     * Expuesto para que handlers lean comandos y streams entrantes sin duplicar lógica,
     * pero sin exponer el {@link Socket} concreto.
     */
    public InputStream inputStream() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public void sendMensaje(Mensaje mensaje) throws IOException {
        byte[] payload = (mensaje.toJson() + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            OutputStream out = socket.getOutputStream();
            out.write(payload);
            out.flush();
        }
    }

    @Override
    public void sendBytes(byte[] data, int offset, int length) throws IOException {
        synchronized (writeLock) {
            OutputStream out = socket.getOutputStream();
            out.write(data, offset, length);
        }
    }

    /**
     * Escribe un entero (4 bytes) seguido de un bloque. Usado para descargas
     * encriptadas con marcador de longitud por bloque.
     */
    public void sendChunkedBlock(byte[] data, int length) throws IOException {
        synchronized (writeLock) {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            dos.writeInt(length);
            if (length > 0) {
                dos.write(data, 0, length);
            }
        }
    }

    public void flush() throws IOException {
        synchronized (writeLock) {
            socket.getOutputStream().flush();
        }
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            // nada
        }
    }
}
