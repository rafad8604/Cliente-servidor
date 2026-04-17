package com.app.server.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Redirige System.out/System.err a un archivo de log por ejecución,
 * manteniendo también la salida en consola (modo tee).
 */
public class SessionLogManager implements Closeable {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PrintStream originalOut;
    private final PrintStream originalErr;
    private final PrintStream teeOut;
    private final PrintStream teeErr;
    private final OutputStream fileOutput;
    private final Path logFile;

    private SessionLogManager(Path logFile,
                              PrintStream originalOut,
                              PrintStream originalErr,
                              OutputStream fileOutput,
                              PrintStream teeOut,
                              PrintStream teeErr) {
        this.logFile = logFile;
        this.originalOut = originalOut;
        this.originalErr = originalErr;
        this.fileOutput = fileOutput;
        this.teeOut = teeOut;
        this.teeErr = teeErr;
    }

    public static SessionLogManager start() throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        Path logDir = Paths.get("./storage/server-logs");
        Files.createDirectories(logDir);

        String fileName = "server-" + LocalDateTime.now().format(FILE_TS) + ".log";
        Path logFile = logDir.resolve(fileName).toAbsolutePath().normalize();

        OutputStream rawFileOut = Files.newOutputStream(logFile);
        OutputStream syncFileOut = new SynchronizedOutputStream(rawFileOut);

        PrintStream teeOut = new PrintStream(
                new TeeOutputStream(originalOut, syncFileOut),
                true,
                StandardCharsets.UTF_8
        );
        PrintStream teeErr = new PrintStream(
                new TeeOutputStream(originalErr, syncFileOut),
                true,
                StandardCharsets.UTF_8
        );

        System.setOut(teeOut);
        System.setErr(teeErr);

        SessionLogManager manager = new SessionLogManager(
                logFile,
                originalOut,
                originalErr,
                syncFileOut,
                teeOut,
                teeErr
        );

        System.out.println("[LOG] Sesión iniciada. Archivo: " + manager.getLogFilePath());
        return manager;
    }

    public String getLogFilePath() {
        return logFile.toString();
    }

    @Override
    public void close() {
        try {
            System.out.println("[LOG] Sesión finalizada.");
        } catch (Exception ignored) {
        }

        System.setOut(originalOut);
        System.setErr(originalErr);

        try {
            teeOut.flush();
        } catch (Exception ignored) {
        }

        try {
            teeErr.flush();
        } catch (Exception ignored) {
        }

        try {
            fileOutput.flush();
        } catch (Exception ignored) {
        }

        try {
            teeOut.close();
        } catch (Exception ignored) {
        }

        try {
            teeErr.close();
        } catch (Exception ignored) {
        }

        try {
            fileOutput.close();
        } catch (Exception ignored) {
        }
    }

    private static class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            first.write(b, off, len);
            second.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    private static class SynchronizedOutputStream extends OutputStream {
        private final OutputStream delegate;

        SynchronizedOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public synchronized void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public synchronized void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public synchronized void close() throws IOException {
            delegate.close();
        }
    }
}
