package com.app.client.dao;

import java.sql.*;

/**
 * Conexión embebida a H2 para el cliente.
 * Crea la base de datos y tabla automáticamente al iniciar.
 */
public class H2Connection {

    private static final String DB_DIR = System.getProperty("user.home") + "/.mensajeria_client";
    private static final String URL = "jdbc:h2:" + DB_DIR + "/clientdb;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private static H2Connection instance;
    private Connection connection;

    private H2Connection() {
    }

    public static synchronized H2Connection getInstance() {
        if (instance == null) {
            instance = new H2Connection();
        }
        return instance;
    }

    /**
     * Inicializa la conexión y crea las tablas si no existen.
     */
    public void init() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("H2 Driver no encontrado", e);
        }

        // Crear directorio si no existe
        new java.io.File(DB_DIR).mkdirs();

        connection = DriverManager.getConnection(URL, USER, PASSWORD);

        // Crear tabla de historial
        String createTable = """
                CREATE TABLE IF NOT EXISTS historial_documentos (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    nombre VARCHAR(500) NOT NULL,
                    tamano BIGINT NOT NULL,
                    tipo VARCHAR(20) NOT NULL,
                    direccion VARCHAR(10) NOT NULL,
                    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }

        System.out.println("[H2] Base de datos inicializada en: " + DB_DIR);
    }

    /**
     * Obtiene la conexión activa.
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        }
        return connection;
    }

    /**
     * Cierra la conexión.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("[H2] Error cerrando conexión: " + e.getMessage());
        }
    }
}
