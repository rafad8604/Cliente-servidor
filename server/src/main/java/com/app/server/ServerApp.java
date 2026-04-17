package com.app.server;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.dao.DatabaseConnection;
import com.app.server.net.ServerCore;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.server.util.SessionLogManager;
import com.app.shared.util.CryptoUtil;

import javax.crypto.SecretKey;
import java.util.Scanner;

/**
 * Punto de entrada del servidor.
 * Inicializa la base de datos, genera clave de sesión, y arranca los listeners TCP/UDP.
 */
public class ServerApp {

    private static final int TCP_PORT = 9000;
    private static final int UDP_PORT = 9001;
    private static final int MAX_CLIENTS = 10;

    public static void main(String[] args) {
        SessionLogManager sessionLog = null;
        int exitCode = 0;

        try {
            sessionLog = SessionLogManager.start();
        } catch (Exception e) {
            System.err.println("[LOG] No se pudo iniciar el log de sesión: " + e.getMessage());
        }

        System.out.println("============================================");
        System.out.println("  SERVIDOR DE MENSAJERÍA Y ARCHIVOS");
        System.out.println("  TCP: " + TCP_PORT + " | UDP: " + UDP_PORT);
        System.out.println("============================================");

        try {
            // 1. Inicializar base de datos
            System.out.println("[INIT] Conectando a MySQL...");
            DatabaseConnection.getInstance().init();

            // Limpiar clientes conectados del reinicio anterior
            new ClienteConectadoDAO().limpiarTodos();

            // 2. Generar clave AES de sesión
            SecretKey sessionKey = CryptoUtil.generateAESKey();
            System.out.println("[INIT] Clave AES-256 de sesión generada.");
            System.out.println("[INIT] Clave (Base64): " + CryptoUtil.keyToBase64(sessionKey));

            // 3. Inicializar servicios
            LogService logService = new LogService();
            DocumentoService documentoService = new DocumentoService(sessionKey);

            // 4. Arrancar servidor
            ServerCore server = new ServerCore(TCP_PORT, UDP_PORT, MAX_CLIENTS, documentoService, logService);
            server.start();

            logService.registrar("SERVIDOR_INICIADO", "localhost",
                    "TCP:" + TCP_PORT + " UDP:" + UDP_PORT + " MaxClientes:" + MAX_CLIENTS);

            // 5. Esperar comando de salida
            System.out.println("\nEscribe 'exit' para detener el servidor...\n");
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(line)) {
                    break;
                } else if ("status".equalsIgnoreCase(line)) {
                    System.out.println("Clientes activos: " + server.getClientPool().getActiveCount() +
                            "/" + server.getClientPool().getMaxClients());
                }
            }

            // 6. Shutdown
            System.out.println("[SHUTDOWN] Deteniendo servidor...");
            server.stop();
            DatabaseConnection.getInstance().shutdown();
            logService.registrar("SERVIDOR_DETENIDO", "localhost", "Servidor detenido manualmente");
            System.out.println("[SHUTDOWN] Servidor detenido correctamente.");

        } catch (Exception e) {
            System.err.println("[ERROR FATAL] " + e.getMessage());
            e.printStackTrace();
            exitCode = 1;
        } finally {
            if (sessionLog != null) {
                sessionLog.close();
            }
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
