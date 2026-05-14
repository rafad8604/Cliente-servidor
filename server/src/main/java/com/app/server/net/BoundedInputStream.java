package com.app.server.net;

import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream que limita la lectura a un numero maximo de bytes.
 */
public final class BoundedInputStream extends InputStream {
    private final InputStream in;
    private long remaining;

    public BoundedInputStream(InputStream in, long limit) {
        this.in = in;
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) return -1;
        int b = in.read();
        if (b != -1) remaining--;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) return -1;
        int toRead = (int) Math.min(len, remaining);
        int bytesRead = in.read(b, off, toRead);
        if (bytesRead > 0) remaining -= bytesRead;
        return bytesRead;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }
}
