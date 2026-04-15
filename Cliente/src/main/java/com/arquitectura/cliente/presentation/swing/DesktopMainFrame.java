package com.arquitectura.cliente.presentation.swing;

import com.arquitectura.cliente.application.ConnectionManager;
import com.arquitectura.cliente.application.QueryService;
import com.arquitectura.cliente.application.TransferService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Ventana principal de la aplicación cliente de escritorio.
 */
@Component
@ConditionalOnProperty(name = "cliente.ui.enabled", havingValue = "true", matchIfMissing = true)
public class DesktopMainFrame extends JFrame {

    private final ConnectionManager connectionManager;
    private final QueryService queryService;
    private final TransferService transferService;

    private JTextField hostField;
    private JTextField portField;
    private JComboBox<String> protocolCombo;
    private JButton connectBtn;
    private JButton disconnectBtn;
    private JLabel statusLabel;

    private JTabbedPane tabbedPane;
    private JTextArea clientsArea;
    private JTextArea documentsArea;
    private JTextArea logsArea;
    private JTextArea messagesArea;
    private JTextField messageRecipientField;

    public DesktopMainFrame(ConnectionManager connectionManager,
                            QueryService queryService,
                            TransferService transferService) {
        this.connectionManager = connectionManager;
        this.queryService = queryService;
        this.transferService = transferService;

        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setTitle("Cliente TCP/UDP - Gestor de Documentos");
    }

    private void initUI() {
        // Panel de conexión
        JPanel connectionPanel = createConnectionPanel();
        
        // Panel de pestañas con funcionalidades
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Clientes Conectados", createClientsPanel());
        tabbedPane.addTab("Documentos Disponibles", createDocumentsPanel());
        tabbedPane.addTab("Enviar Mensaje", createMessagesPanel());
        tabbedPane.addTab("Enviar Archivo", createUploadPanel());
        tabbedPane.addTab("Logs del Servidor", createLogsPanel());
        tabbedPane.setEnabled(false);

        // Layout principal
        setLayout(new BorderLayout(5, 5));
        add(connectionPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Conexión al Servidor"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Host
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("IP/Host:"), gbc);
        hostField = new JTextField("localhost", 15);
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        // Puerto
        gbc.gridx = 2;
        panel.add(new JLabel("Puerto:"), gbc);
        portField = new JTextField("5000", 8);
        gbc.gridx = 3;
        panel.add(portField, gbc);

        // Protocolo
        gbc.gridx = 4;
        panel.add(new JLabel("Protocolo:"), gbc);
        protocolCombo = new JComboBox<>(new String[]{"TCP", "UDP"});
        gbc.gridx = 5;
        panel.add(protocolCombo, gbc);

        // Botones
        connectBtn = new JButton("Conectar");
        connectBtn.addActionListener(e -> onConnect());
        gbc.gridx = 6;
        panel.add(connectBtn, gbc);

        disconnectBtn = new JButton("Desconectar");
        disconnectBtn.setEnabled(false);
        disconnectBtn.addActionListener(e -> onDisconnect());
        gbc.gridx = 7;
        panel.add(disconnectBtn, gbc);

        return panel;
    }

    private JPanel createClientsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        clientsArea = new JTextArea();
        clientsArea.setEditable(false);
        clientsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        JButton refreshBtn = new JButton("Actualizar");
        refreshBtn.addActionListener(e -> refreshClients());
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(refreshBtn);
        
        panel.add(new JScrollPane(clientsArea), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createDocumentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        documentsArea = new JTextArea();
        documentsArea.setEditable(false);
        documentsArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        JButton refreshBtn = new JButton("Actualizar");
        refreshBtn.addActionListener(e -> refreshDocuments());
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(refreshBtn);
        
        panel.add(new JScrollPane(documentsArea), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createMessagesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Destinatario:"));
        messageRecipientField = new JTextField("cliente1", 20);
        topPanel.add(messageRecipientField);
        
        messagesArea = new JTextArea();
        messagesArea.setLineWrap(true);
        messagesArea.setWrapStyleWord(true);
        messagesArea.setRows(5);
        
        JButton sendBtn = new JButton("Enviar Mensaje");
        sendBtn.addActionListener(e -> sendMessage());
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(sendBtn);
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(messagesArea), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createUploadPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("Selecciona archivos para enviar (funcionalidad en desarrollo)");
        JButton selectBtn = new JButton("Seleccionar Archivo");
        selectBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, "Funcionalidad en desarrollo"));
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(selectBtn);
        
        panel.add(label, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        logsArea = new JTextArea();
        logsArea.setEditable(false);
        logsArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        
        JButton refreshBtn = new JButton("Actualizar");
        refreshBtn.addActionListener(e -> refreshLogs());
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(refreshBtn);
        
        panel.add(new JScrollPane(logsArea), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel();
        statusLabel = new JLabel("Desconectado");
        panel.add(statusLabel);
        return panel;
    }

    private void onConnect() {
        try {
            String host = hostField.getText();
            int port = Integer.parseInt(portField.getText());
            String protocol = (String) protocolCombo.getSelectedItem();

            connectionManager.connect(host, port, protocol);
            
            connectBtn.setEnabled(false);
            disconnectBtn.setEnabled(true);
            tabbedPane.setEnabled(true);
            statusLabel.setText("✓ Conectado: " + host + ":" + port + " (" + protocol + ")");
            
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Puerto inválido", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error de conexión: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDisconnect() {
        try {
            connectionManager.disconnect();
            connectBtn.setEnabled(true);
            disconnectBtn.setEnabled(false);
            tabbedPane.setEnabled(false);
            statusLabel.setText("Desconectado");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error al desconectar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshClients() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                int count = queryService.getConnectedClientsCount();
                List<Map<String, String>> clients = queryService.getConnectedClientsList();
                
                StringBuilder sb = new StringBuilder();
                sb.append("Total de clientes conectados: ").append(count).append("\n\n");
                for (Map<String, String> client : clients) {
                    sb.append("IP: ").append(client.get("ipAddress")).append("\n");
                    sb.append("  Conectado desde: ").append(client.get("startDate")).append(" ").append(client.get("startTime")).append("\n\n");
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                try {
                    clientsArea.setText(get());
                } catch (Exception ex) {
                    clientsArea.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void refreshDocuments() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                List<Map<String, Object>> docs = queryService.getAvailableDocuments();
                
                StringBuilder sb = new StringBuilder();
                sb.append("Documentos disponibles:\n\n");
                for (Map<String, Object> doc : docs) {
                    sb.append("ID: ").append(doc.get("documentId")).append("\n");
                    sb.append("Nombre: ").append(doc.get("name")).append("\n");
                    sb.append("Tamaño: ").append(doc.get("sizeBytes")).append(" bytes\n");
                    sb.append("Extensión: ").append(doc.get("extension")).append("\n");
                    sb.append("Propietario: ").append(doc.get("owner")).append(" (").append(doc.get("ownerIp")).append(")\n\n");
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                try {
                    documentsArea.setText(get());
                } catch (Exception ex) {
                    documentsArea.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void refreshLogs() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                List<Map<String, Object>> logs = queryService.getLogs();
                
                StringBuilder sb = new StringBuilder();
                sb.append("Logs del servidor:\n\n");
                for (Map<String, Object> log : logs) {
                    sb.append("[").append(log.get("timestamp")).append("] ");
                    sb.append(log.get("senderId")).append(" - ");
                    sb.append(log.get("documentType")).append(" - ");
                    sb.append(log.get("fileName")).append("\n");
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                try {
                    logsArea.setText(get());
                } catch (Exception ex) {
                    logsArea.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void sendMessage() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String senderId = "desktop-client";
                String recipientId = messageRecipientField.getText();
                String message = messagesArea.getText();
                
                return transferService.sendMessage(senderId, recipientId, message);
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    JOptionPane.showMessageDialog(DesktopMainFrame.this, 
                        "Mensaje enviado exitosamente:\n" + response, 
                        "Éxito", 
                        JOptionPane.INFORMATION_MESSAGE);
                    messagesArea.setText("");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DesktopMainFrame.this, 
                        "Error enviando mensaje: " + ex.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
