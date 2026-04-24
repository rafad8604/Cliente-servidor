package com.app.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Pool de conexiones JDBC básico para MySQL.
 * Usa una BlockingQueue para gestionar las conexiones.
 */
public class DatabaseConnection {

    private static final String URL = "jdbc:mysql://localhost:33306/mensajeria_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "mensajeria_user";
    private static final String PASSWORD = "mensajeria_pass";
    private static final int POOL_SIZE = 10;

    private static DatabaseConnection instance;
    private final BlockingQueue<Connection> connectionPool;
    private boolean initialized = false;

    private DatabaseConnection() {
        this.connectionPool = new ArrayBlockingQueue<>(POOL_SIZE);
    }

    /**
     * Obtiene la instancia singleton.
     */
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Inicializa el pool de conexiones.
     */
    public synchronized void init() throws SQLException {
        if (initialized) return;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver no encontrado", e);
        }

        for (int i = 0; i < POOL_SIZE; i++) {
            Connection conn = createConnection();
            connectionPool.offer(conn);
        }

        initialized = true;
        System.out.println("[DB] Pool de conexiones inicializado con " + POOL_SIZE + " conexiones.");
    }

    /**
     * Obtiene una conexión del pool. Bloquea si no hay disponibles.
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection conn = connectionPool.take();
            // Verificar que la conexión sigue viva
            if (conn.isClosed() || !conn.isValid(2)) {
                conn = createConnection();
            }
            return conn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrumpido esperando conexión", e);
        }
    }

    /**
     * Devuelve una conexión al pool.
     */
    public void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(2)) {
                    if (!conn.getAutoCommit()) {
                        conn.setAutoCommit(true);
                    }
                    connectionPool.offer(conn);
                } else {
                    // Reemplazar conexión inválida
                    connectionPool.offer(createConnection());
                }
            } catch (SQLException e) {
                System.err.println("[DB] Error al devolver conexión: " + e.getMessage());
                try {
                    connectionPool.offer(createConnection());
                } catch (SQLException ex) {
                    System.err.println("[DB] Error creando conexión de reemplazo: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Cierra todas las conexiones del pool.
     */
    public void shutdown() {
        for (Connection conn : connectionPool) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("[DB] Error cerrando conexión: " + e.getMessage());
            }
        }
        connectionPool.clear();
        initialized = false;
        System.out.println("[DB] Pool de conexiones cerrado.");
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
