package com.app.server.net;

import com.app.shared.protocol.Mensaje;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstracción de un canal de comunicación con un cliente.
 *
 * Unifica las operaciones sobre TCP (Socket) y UDP (DatagramSocket + sesión),
 * evitando que capas superiores dependan de clases concretas de red.
 *
 * Implementaciones concretas:
 * - {@link TcpClientChannel}
 * - {@link UdpClientChannel}
 */
public interface ClientChannel extends Closeable {

    /**
     * Contexto del cliente remoto (ip, puerto, protocolo).
     */
    ClientContext getContext();

    /**
     * Envía un {@link Mensaje} de control al cliente.
     */
    void sendMensaje(Mensaje mensaje) throws IOException;

    /**
     * Envía un bloque binario al cliente (útil para streams de descarga).
     */
    void sendBytes(byte[] data, int offset, int length) throws IOException;

    /**
     * Indica si el canal aún está abierto.
     */
    boolean isOpen();

    @Override
    void close();
}
