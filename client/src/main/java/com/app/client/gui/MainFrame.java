package com.app.client.gui;

import com.app.client.dao.HistorialDAO;
import com.app.client.models.HistorialDocumento;
import com.app.client.net.NetworkClient;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interfaz gráfica principal del cliente de mensajería.
 * Incluye: Configuración, Clientes Conectados, Documentos, y Chat.
 */
public class MainFrame extends JFrame {

    // Colores del tema oscuro moderno
    private static final Color BG_DARK = new Color(30, 30, 46);
    private static final Color BG_PANEL = new Color(40, 42, 58);
    private static final Color BG_INPUT = new Color(50, 52, 70);
    private static final Color ACCENT = new Color(137, 180, 250);
    private static final Color ACCENT_HOVER = new Color(116, 160, 235);
    private static final Color TEXT_PRIMARY = new Color(205, 214, 244);
    private static final Color TEXT_SECONDARY = new Color(147, 153, 178);
    private static final Color SUCCESS = new Color(166, 227, 161);
    private static final Color ERROR_COLOR = new Color(243, 139, 168);
    private static final Color BORDER_COLOR = new Color(69, 71, 90);

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font FONT_NORMAL = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    // Componentes de configuración
    private JTextField txtHost;
    private JTextField txtPort;
    private JRadioButton rbTcp;
    private JRadioButton rbUdp;
    private JButton btnConectar;
    private JLabel lblEstado;

    // Componentes de clientes
    private DefaultTableModel modelClientes;
    private JTable tblClientes;

    // Componentes de documentos
    private DefaultTableModel modelDocumentos;
    private JTable tblDocumentos;

    // Componentes de chat
    private JTextArea txtChat;
    private JTextField txtMensaje;
    private JButton btnEnviar;
    private JButton btnAdjuntar;
    private JProgressBar progressBar;
    private JLabel lblProgress;

    // Red y datos
    private NetworkClient networkClient;
    private HistorialDAO historialDAO;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MainFrame() {
        super("📡 Sistema de Mensajería y Archivos");
        this.historialDAO = new HistorialDAO();
        initComponents();
        setupLayout();
        setupEvents();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        // Icono de la ventana (usar un carácter emoji como placeholder)
        setIconImage(createAppIcon());
    }

    private void initComponents() {
        // --- Configuración ---
        txtHost = createStyledTextField("127.0.0.1", 12);
        txtPort = createStyledTextField("9000", 6);

        rbTcp = new JRadioButton("TCP");
        rbTcp.setSelected(true);
        rbTcp.setForeground(TEXT_PRIMARY);
        rbTcp.setBackground(BG_PANEL);
        rbTcp.setFont(FONT_NORMAL);
        rbTcp.setFocusPainted(false);

        rbUdp = new JRadioButton("UDP");
        rbUdp.setForeground(TEXT_PRIMARY);
        rbUdp.setBackground(BG_PANEL);
        rbUdp.setFont(FONT_NORMAL);
        rbUdp.setFocusPainted(false);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbTcp);
        bg.add(rbUdp);

        btnConectar = createStyledButton("⚡ Conectar", ACCENT);
        lblEstado = new JLabel("● Desconectado");
        lblEstado.setFont(FONT_SMALL);
        lblEstado.setForeground(ERROR_COLOR);

        // --- Tabla de clientes ---
        modelClientes = new DefaultTableModel(
                new String[]{"IP", "Puerto", "Protocolo", "Conectado desde"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblClientes = createStyledTable(modelClientes);

        // --- Tabla de documentos ---
        modelDocumentos = new DefaultTableModel(
                new String[]{"ID", "Nombre", "Extensión", "Tamaño", "Tipo", "Hash SHA-256", "IP Origen"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblDocumentos = createStyledTable(modelDocumentos);

        // --- Chat ---
        txtChat = new JTextArea();
        txtChat.setEditable(false);
        txtChat.setBackground(BG_INPUT);
        txtChat.setForeground(TEXT_PRIMARY);
        txtChat.setFont(FONT_MONO);
        txtChat.setCaretColor(ACCENT);
        txtChat.setLineWrap(true);
        txtChat.setWrapStyleWord(true);
        txtChat.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        txtMensaje = createStyledTextField("Escribe un mensaje...", 30);
        txtMensaje.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (txtMensaje.getText().equals("Escribe un mensaje...")) {
                    txtMensaje.setText("");
                    txtMensaje.setForeground(TEXT_PRIMARY);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (txtMensaje.getText().isEmpty()) {
                    txtMensaje.setText("Escribe un mensaje...");
                    txtMensaje.setForeground(TEXT_SECONDARY);
                }
            }
        });
        txtMensaje.setForeground(TEXT_SECONDARY);

        btnEnviar = createStyledButton("Enviar", ACCENT);
        btnAdjuntar = createStyledButton("📎 Adjuntar", new Color(166, 227, 161));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(FONT_SMALL);
        progressBar.setBackground(BG_INPUT);
        progressBar.setForeground(ACCENT);
        progressBar.setBorderPainted(false);
        progressBar.setVisible(false);

        lblProgress = new JLabel(" ");
        lblProgress.setFont(FONT_SMALL);
        lblProgress.setForeground(TEXT_SECONDARY);
    }

    private void setupLayout() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_DARK);

        // === Panel Superior: Configuración de conexión ===
        JPanel panelConfig = createConfigPanel();

        // === Panel Central: Dividido en 3 secciones ===
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setBackground(BG_DARK);
        mainSplit.setBorder(null);
        mainSplit.setDividerLocation(380);
        mainSplit.setDividerSize(3);

        // Lado izquierdo: Chat
        JPanel panelChat = createChatPanel();

        // Lado derecho: Clientes + Documentos (vertical split)
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplit.setBackground(BG_DARK);
        rightSplit.setBorder(null);
        rightSplit.setDividerLocation(220);
        rightSplit.setDividerSize(3);

        JPanel panelClientes = createClientesPanel();
        JPanel panelDocumentos = createDocumentosPanel();

        rightSplit.setTopComponent(panelClientes);
        rightSplit.setBottomComponent(panelDocumentos);

        mainSplit.setLeftComponent(panelChat);
        mainSplit.setRightComponent(rightSplit);

        // === Panel Inferior: Barra de progreso ===
        JPanel panelFooter = new JPanel(new BorderLayout(8, 0));
        panelFooter.setBackground(BG_DARK);
        panelFooter.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        panelFooter.add(progressBar, BorderLayout.CENTER);
        panelFooter.add(lblProgress, BorderLayout.EAST);

        add(panelConfig, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(panelFooter, BorderLayout.SOUTH);
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JLabel lblTitle = new JLabel("🌐 Conexión");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(TEXT_PRIMARY);

        panel.add(lblTitle);
        panel.add(createLabel("Host:"));
        panel.add(txtHost);
        panel.add(createLabel("Puerto:"));
        panel.add(txtPort);
        panel.add(Box.createHorizontalStrut(8));
        panel.add(rbTcp);
        panel.add(rbUdp);
        panel.add(Box.createHorizontalStrut(8));
        panel.add(btnConectar);
        panel.add(Box.createHorizontalStrut(12));
        panel.add(lblEstado);

        return panel;
    }

    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.setBackground(BG_PANEL);
        JLabel lbl = new JLabel("💬 Chat y Mensajes");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);
        header.add(lbl);

        // Scroll del chat
        JScrollPane scrollChat = new JScrollPane(txtChat);
        scrollChat.setBorder(createRoundedBorder());
        scrollChat.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollBar(scrollChat);

        // Panel de entrada
        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setBackground(BG_PANEL);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonsPanel.setBackground(BG_PANEL);
        buttonsPanel.add(btnAdjuntar);
        buttonsPanel.add(btnEnviar);

        inputPanel.add(txtMensaje, BorderLayout.CENTER);
        inputPanel.add(buttonsPanel, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollChat, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createClientesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        JLabel lbl = new JLabel("👥 Clientes Conectados");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);

        JButton btnRefreshClientes = createStyledButton("🔄 Refrescar", ACCENT);
        btnRefreshClientes.addActionListener(e -> refrescarClientes());

        header.add(lbl, BorderLayout.WEST);
        header.add(btnRefreshClientes, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(tblClientes);
        scroll.setBorder(createRoundedBorder());
        styleScrollBar(scroll);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDocumentosPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        JLabel lbl = new JLabel("📄 Documentos en el Servidor");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setBackground(BG_PANEL);

        JButton btnRefreshDocs = createStyledButton("🔄 Refrescar", ACCENT);
        JButton btnDescargarOrig = createStyledButton("📥 Original", SUCCESS);
        JButton btnDescargarHash = createStyledButton("#️⃣ Hash", new Color(249, 226, 175));
        JButton btnDescargarEnc = createStyledButton("🔒 Encriptado", new Color(203, 166, 247));

        btnRefreshDocs.addActionListener(e -> refrescarDocumentos());
        btnDescargarOrig.addActionListener(e -> descargarSeleccionado("ORIGINAL"));
        btnDescargarHash.addActionListener(e -> descargarSeleccionado("HASH"));
        btnDescargarEnc.addActionListener(e -> descargarSeleccionado("ENCRIPTADO"));

        btnPanel.add(btnRefreshDocs);
        btnPanel.add(btnDescargarOrig);
        btnPanel.add(btnDescargarHash);
        btnPanel.add(btnDescargarEnc);

        header.add(lbl, BorderLayout.WEST);
        header.add(btnPanel, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(tblDocumentos);
        scroll.setBorder(createRoundedBorder());
        styleScrollBar(scroll);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void setupEvents() {
        btnConectar.addActionListener(e -> toggleConexion());

        btnEnviar.addActionListener(e -> enviarMensaje());
        txtMensaje.addActionListener(e -> enviarMensaje());

        btnAdjuntar.addActionListener(e -> adjuntarArchivos());

        // Cleanup al cerrar
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
            }
        });
    }

    // ==================== Acciones ====================

    private void toggleConexion() {
        if (networkClient != null && networkClient.isConnected()) {
            desconectar();
        } else {
            conectar();
        }
    }

    private void conectar() {
        String host = txtHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            showError("Puerto inválido");
            return;
        }

        NetworkClient.Protocolo proto = rbTcp.isSelected() ?
                NetworkClient.Protocolo.TCP : NetworkClient.Protocolo.UDP;

        btnConectar.setEnabled(false);
        appendChat("[SISTEMA] Conectando a " + host + ":" + port + " (" + proto + ")...", TEXT_SECONDARY);

        // Conectar en hilo separado para no bloquear la GUI
        CompletableFuture.supplyAsync(() -> {
            try {
                NetworkClient client = new NetworkClient(host, port, proto);
                Mensaje sesion = client.conectar();

                client.setOnMessageReceived(msg -> {
                    SwingUtilities.invokeLater(() -> {
                        String texto = msg.getString("texto");
                        String remitente = msg.getString("remitente");
                        appendChat("[" + remitente + "] " + texto, ACCENT);
                    });
                });

                return client;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(client -> {
            SwingUtilities.invokeLater(() -> {
                networkClient = client;
                btnConectar.setText("⛔ Desconectar");
                btnConectar.setEnabled(true);
                lblEstado.setText("● Conectado (" + proto + ")");
                lblEstado.setForeground(SUCCESS);
                appendChat("[SISTEMA] ¡Conectado exitosamente!", SUCCESS);

                setInputsEnabled(false);

                // Auto-refresh
                refrescarClientes();
                refrescarDocumentos();
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                btnConectar.setEnabled(true);
                appendChat("[ERROR] No se pudo conectar: " + ex.getCause().getMessage(), ERROR_COLOR);
                showError("Error al conectar: " + ex.getCause().getMessage());
            });
            return null;
        });
    }

    private void desconectar() {
        if (networkClient != null) {
            try {
                networkClient.close();
            } catch (Exception e) {
                // ignore
            }
            networkClient = null;
        }
        btnConectar.setText("⚡ Conectar");
        lblEstado.setText("● Desconectado");
        lblEstado.setForeground(ERROR_COLOR);
        setInputsEnabled(true);
        appendChat("[SISTEMA] Desconectado del servidor.", TEXT_SECONDARY);
    }

    private void enviarMensaje() {
        if (networkClient == null || !networkClient.isConnected()) {
            showError("No estás conectado al servidor");
            return;
        }

        String texto = txtMensaje.getText().trim();
        if (texto.isEmpty() || texto.equals("Escribe un mensaje...")) return;

        txtMensaje.setText("");
        appendChat("[TÚ] " + texto, new Color(166, 227, 161));

        CompletableFuture.supplyAsync(() -> {
            try {
                return networkClient.enviarMensaje(texto);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            SwingUtilities.invokeLater(() -> {
                String hash = resp.getString("hash");
                appendChat("  ↳ Hash: " + hash, TEXT_SECONDARY);

                // Registrar en historial local
                try {
                    historialDAO.registrar(new HistorialDocumento(
                            "mensaje", texto.length(), "MENSAJE", HistorialDocumento.Direccion.ENVIADO));
                } catch (Exception e) {
                    // ignore
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
            return null;
        });
    }

    private void adjuntarArchivos() {
        if (networkClient == null || !networkClient.isConnected()) {
            showError("No estás conectado al servidor");
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle("Seleccionar archivos para enviar");

        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File[] files = fc.getSelectedFiles();
        if (files.length == 0) return;

        appendChat("[SISTEMA] Enviando " + files.length + " archivo(s)...", TEXT_SECONDARY);

        progressBar.setVisible(true);
        progressBar.setValue(0);

        for (File file : files) {
            long fileSize = file.length();
            appendChat("  📤 " + file.getName() + " (" + formatSize(fileSize) + ")", ACCENT);

            networkClient.enviarArchivo(file, bytesEnviados -> {
                SwingUtilities.invokeLater(() -> {
                    int pct = (int) ((bytesEnviados * 100) / fileSize);
                    progressBar.setValue(pct);
                    lblProgress.setText(formatSize(bytesEnviados) + " / " + formatSize(fileSize));
                });
            }).thenAccept(resp -> {
                SwingUtilities.invokeLater(() -> {
                    String hash = resp.getString("hash");
                    appendChat("  ✅ " + file.getName() + " enviado. Hash: " + hash, SUCCESS);
                    progressBar.setValue(100);
                    lblProgress.setText("Completado");

                    try {
                        historialDAO.registrar(new HistorialDocumento(
                                file.getName(), fileSize, "ARCHIVO", HistorialDocumento.Direccion.ENVIADO));
                    } catch (Exception e) {
                        // ignore
                    }

                    // Ocultar progress después de 2s
                    Timer timer = new Timer(2000, evt -> {
                        progressBar.setVisible(false);
                        lblProgress.setText(" ");
                    });
                    timer.setRepeats(false);
                    timer.start();
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    appendChat("  ❌ Error enviando " + file.getName() + ": " + ex.getCause().getMessage(), ERROR_COLOR);
                    progressBar.setVisible(false);
                });
                return null;
            });
        }
    }

    private void refrescarClientes() {
        if (networkClient == null || !networkClient.isConnected()) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                return networkClient.listarClientes();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            SwingUtilities.invokeLater(() -> {
                modelClientes.setRowCount(0);
                String clientesJson = resp.getString("clientes");
                if (clientesJson != null) {
                    try {
                        Gson gson = new Gson();
                        java.lang.reflect.Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                        List<Map<String, Object>> clientes = gson.fromJson(clientesJson, (java.lang.reflect.Type) listType);
                        for (Map<String, Object> c : clientes) {
                            modelClientes.addRow(new Object[]{
                                    c.get("ip"),
                                    c.get("puerto") instanceof Number ?
                                            ((Number) c.get("puerto")).intValue() : c.get("puerto"),
                                    c.get("protocolo"),
                                    c.get("fechaInicio")
                            });
                        }
                    } catch (Exception e) {
                        appendChat("[ERROR] Parseando clientes: " + e.getMessage(), ERROR_COLOR);
                    }
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
            return null;
        });
    }

    private void refrescarDocumentos() {
        if (networkClient == null || !networkClient.isConnected()) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                return networkClient.listarDocumentos();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            SwingUtilities.invokeLater(() -> {
                modelDocumentos.setRowCount(0);
                String docsJson = resp.getString("documentos");
                if (docsJson != null) {
                    try {
                        Gson gson = new Gson();
                        java.lang.reflect.Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                        List<Map<String, Object>> docs = gson.fromJson(docsJson, (java.lang.reflect.Type) listType);
                        for (Map<String, Object> d : docs) {
                            long tamano = d.get("tamano") instanceof Number ?
                                    ((Number) d.get("tamano")).longValue() : 0;
                            long id = d.get("id") instanceof Number ?
                                    ((Number) d.get("id")).longValue() : 0;
                            modelDocumentos.addRow(new Object[]{
                                    id,
                                    d.get("nombre"),
                                    d.get("extension"),
                                    formatSize(tamano),
                                    d.get("tipo"),
                                    d.get("hash"),
                                    d.get("ip")
                            });
                        }
                    } catch (Exception e) {
                        appendChat("[ERROR] Parseando documentos: " + e.getMessage(), ERROR_COLOR);
                    }
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
            return null;
        });
    }

    private void descargarSeleccionado(String tipo) {
        if (networkClient == null || !networkClient.isConnected()) {
            showError("No estás conectado al servidor");
            return;
        }

        int row = tblDocumentos.getSelectedRow();
        if (row == -1) {
            showError("Selecciona un documento de la tabla");
            return;
        }

        Object idObj = modelDocumentos.getValueAt(row, 0);
        long docId = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(idObj.toString());
        String nombre = modelDocumentos.getValueAt(row, 1).toString();

        if ("HASH".equals(tipo)) {
            // Solo mostrar el hash
            CompletableFuture.supplyAsync(() -> {
                try {
                    return networkClient.descargarHash(docId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenAccept(resp -> {
                SwingUtilities.invokeLater(() -> {
                    String hash = resp.getString("hash");
                    appendChat("[HASH] " + nombre + ": " + hash, new Color(249, 226, 175));

                    // Copiar al portapapeles
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(hash), null);
                    appendChat("  ↳ Hash copiado al portapapeles", TEXT_SECONDARY);
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() ->
                        appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
                return null;
            });
            return;
        }

        // Seleccionar destino
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(nombre + ("ENCRIPTADO".equals(tipo) ? ".enc" : "")));
        fc.setDialogTitle("Guardar " + tipo.toLowerCase() + " como...");

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File destino = fc.getSelectedFile();

        progressBar.setVisible(true);
        progressBar.setValue(0);
        appendChat("  📥 Descargando " + tipo.toLowerCase() + ": " + nombre + "...", ACCENT);

        CompletableFuture.runAsync(() -> {
            try {
                if ("ORIGINAL".equals(tipo)) {
                    networkClient.descargarArchivo(docId, destino, bytesRecibidos -> {
                        SwingUtilities.invokeLater(() -> {
                            lblProgress.setText(formatSize(bytesRecibidos) + " recibidos");
                        });
                    });
                } else {
                    networkClient.descargarEncriptado(docId, destino, bytesRecibidos -> {
                        SwingUtilities.invokeLater(() -> {
                            lblProgress.setText(formatSize(bytesRecibidos) + " recibidos");
                        });
                    });
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                appendChat("  ✅ Descarga completada: " + destino.getName(), SUCCESS);
                progressBar.setValue(100);
                lblProgress.setText("Completado");

                try {
                    historialDAO.registrar(new HistorialDocumento(
                            nombre, destino.length(), "ARCHIVO", HistorialDocumento.Direccion.RECIBIDO));
                } catch (Exception e) {
                    // ignore
                }

                Timer timer = new Timer(2000, evt -> {
                    progressBar.setVisible(false);
                    lblProgress.setText(" ");
                });
                timer.setRepeats(false);
                timer.start();
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                appendChat("  ❌ Error descargando: " + ex.getCause().getMessage(), ERROR_COLOR);
                progressBar.setVisible(false);
            });
            return null;
        });
    }

    // ==================== Helpers de UI ====================

    private void appendChat(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String fullText = "[" + time + "] " + text + "\n";
            txtChat.append(fullText);
            // Auto-scroll
            txtChat.setCaretPosition(txtChat.getDocument().getLength());
        });
    }

    private void setInputsEnabled(boolean enabled) {
        txtHost.setEnabled(enabled);
        txtPort.setEnabled(enabled);
        rbTcp.setEnabled(enabled);
        rbUdp.setEnabled(enabled);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private JTextField createStyledTextField(String text, int columns) {
        JTextField tf = new JTextField(text, columns);
        tf.setBackground(BG_INPUT);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(ACCENT);
        tf.setFont(FONT_NORMAL);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return tf;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(bgColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bgColor.brighter());
                } else {
                    g2.setColor(bgColor);
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();

                super.paintComponent(g);
            }
        };
        btn.setForeground(BG_DARK);
        btn.setFont(FONT_NORMAL);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return btn;
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(BG_INPUT);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(FONT_SMALL);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(ACCENT);
        table.setSelectionForeground(BG_DARK);
        table.setRowHeight(28);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        // Header styling
        table.getTableHeader().setBackground(BG_PANEL);
        table.getTableHeader().setForeground(TEXT_SECONDARY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        // Centrar celdas
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < model.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        return table;
    }

    private JLabel createLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_NORMAL);
        lbl.setForeground(TEXT_SECONDARY);
        return lbl;
    }

    private Border createRoundedBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    private void styleScrollBar(JScrollPane sp) {
        sp.getVerticalScrollBar().setBackground(BG_INPUT);
        sp.getHorizontalScrollBar().setBackground(BG_INPUT);
        sp.setBackground(BG_INPUT);
        sp.getViewport().setBackground(BG_INPUT);
    }

    private Image createAppIcon() {
        // Crear un icono simple programáticamente
        int size = 32;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ACCENT);
        g.fillRoundRect(2, 2, size - 4, size - 4, 8, 8);
        g.setColor(BG_DARK);
        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g.drawString("M", 8, 24);
        g.dispose();
        return img;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
