package com.app.client.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.app.client.dao.HistorialDAO;
import com.app.client.models.HistorialDocumento;
import com.app.client.net.ClientDiscoveryService;
import com.app.client.net.DiscoveredServer;
import com.app.client.net.NetworkClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
    private JTextField txtNombre;
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

    private DefaultTableModel modelDocumentosPrivados;
    private JTable tblDocumentosPrivados;

    // Destino de envio (mensaje / archivo)
    private JRadioButton rbEnvioTodos;
    private JRadioButton rbEnvioDirigido;
    private ButtonGroup bgEnvio;

    // Componentes de servidores descubiertos
    private DefaultTableModel modelServidores;
    private JTable tblServidores;

    // Componentes de eventos del servidor
    private DefaultTableModel modelEventos;
    private JTable tblEventos;

    // Componentes de logs (BD)
    private DefaultTableModel modelLogs;
    private JTable tblLogs;

    // Descubrimiento de servidores por broadcast UDP
    private ClientDiscoveryService discoveryService;

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
        super("Sistema de Mensajeria y Archivos");
        this.historialDAO = new HistorialDAO();
        initComponents();
        setupLayout();
        setupEvents();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(950, 650));
        setLocationRelativeTo(null);
        setIconImage(createAppIcon());
        iniciarDiscovery();
    }

    private void iniciarDiscovery() {
        try {
            discoveryService = new ClientDiscoveryService();
            discoveryService.start();
            System.out.println("[GUI] Cliente Swing escuchando descubrimiento UDP (puerto "
                    + ClientDiscoveryService.DISCOVERY_PORT + ")");
            // Refresca la tabla cada 3 s (Timer de Swing -> EDT, seguro para UI).
            Timer t = new Timer(3000, e -> refrescarServidores());
            t.setRepeats(true);
            t.start();
            // Primer refresh inmediato para no mostrar la tabla vacia
            // hasta el primer tick de 3 s.
            refrescarServidores();
        } catch (Exception e) {
            System.err.println("[GUI] No se pudo iniciar descubrimiento: " + e.getMessage());
        }
    }

    private void initComponents() {
        // --- Configuración ---
        txtHost = createStyledTextField("127.0.0.1", 12);
        txtPort = createStyledTextField("9000", 6);
        txtNombre = createStyledTextField(detectarNombreLocal(), 10);

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
        rbUdp.addActionListener(e -> ajustarPuertoPorProtocolo());
        rbTcp.addActionListener(e -> ajustarPuertoPorProtocolo());

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbTcp);
        bg.add(rbUdp);

        btnConectar = createStyledButton("Conectar", ACCENT);
        lblEstado = new JLabel("● Desconectado");
        lblEstado.setFont(FONT_SMALL);
        lblEstado.setForeground(ERROR_COLOR);

        // --- Tabla de clientes ---
        modelClientes = new DefaultTableModel(
                new String[]{"Nombre", "IP", "Puerto", "Protocolo", "Conectado desde", "Servidor", "PeerId"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblClientes = createStyledTable(modelClientes);

        // --- Tabla de documentos ---
        modelDocumentos = new DefaultTableModel(
                new String[]{"ID", "Nombre", "Extensión", "Tamaño", "Tipo", "Hash SHA-256", "IP Origen", "Servidor"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblDocumentos = createStyledTable(modelDocumentos);
        DefaultTableCellRenderer shortIdRenderer = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value == null) { setText(""); return; }
                String s = value.toString();
                setText(s.length() > 8 && !"local".equalsIgnoreCase(s) ? s.substring(0, 8) : s);
            }
        };
        shortIdRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tblDocumentos.getColumnModel().getColumn(7).setCellRenderer(shortIdRenderer);

        modelDocumentosPrivados = new DefaultTableModel(
                new String[]{"ID", "Resumen", "Remitente", "Destinatario", "Origen servidor", "Fecha", "Tipo", "Servidor"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblDocumentosPrivados = createStyledTable(modelDocumentosPrivados);
        tblDocumentosPrivados.getColumnModel().getColumn(7).setCellRenderer(shortIdRenderer);

        rbEnvioTodos = new JRadioButton("Enviar a todos");
        rbEnvioTodos.setSelected(true);
        rbEnvioTodos.setForeground(TEXT_PRIMARY);
        rbEnvioTodos.setBackground(BG_PANEL);
        rbEnvioTodos.setFont(FONT_SMALL);
        rbEnvioDirigido = new JRadioButton("Cliente seleccionado (pestaña Clientes)");
        rbEnvioDirigido.setForeground(TEXT_PRIMARY);
        rbEnvioDirigido.setBackground(BG_PANEL);
        rbEnvioDirigido.setFont(FONT_SMALL);
        bgEnvio = new ButtonGroup();
        bgEnvio.add(rbEnvioTodos);
        bgEnvio.add(rbEnvioDirigido);

        // --- Tabla de servidores online (descubrimiento UDP broadcast) ---
        modelServidores = new DefaultTableModel(
                new String[]{"Nombre", "Host", "TCP", "UDP", "Peer", "ID", "Ultima senal"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblServidores = createStyledTable(modelServidores);

        // --- Tabla de eventos del servidor (en vivo) ---
        modelEventos = new DefaultTableModel(
                new String[]{"Hora", "Servidor", "Tipo", "Origen / Cliente", "Puerto", "Protocolo", "Detalle"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblEventos = createStyledTable(modelEventos);

        // --- Tabla de logs (BD) ---
        modelLogs = new DefaultTableModel(
                new String[]{"Fecha", "Servidor", "Accion", "Cliente", "Detalle"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblLogs = createStyledTable(modelLogs);

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
        btnAdjuntar = createStyledButton("Adjuntar", new Color(166, 227, 161));

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

        // Lado derecho: tabs (Servidores | Clientes | Documentos | Eventos | Logs)
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(TEXT_PRIMARY);
        tabs.setFont(FONT_NORMAL);
        tabs.addTab("Servidores", createServidoresPanel());
        tabs.addTab("Clientes", createClientesPanel());
        tabs.addTab("Documentos", createDocumentosPanel());
        tabs.addTab("Docs. privados", createDocumentosPrivadosPanel());
        tabs.addTab("Eventos", createEventosPanel());
        tabs.addTab("Logs (BD)", createLogsPanel());

        mainSplit.setLeftComponent(panelChat);
        mainSplit.setRightComponent(tabs);

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

        JLabel lblTitle = new JLabel("Conexion");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(TEXT_PRIMARY);

        panel.add(lblTitle);
        panel.add(createLabel("Nombre:"));
        panel.add(txtNombre);
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
        JLabel lbl = new JLabel("Chat y Mensajes");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);
        header.add(lbl);

        JPanel destinoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        destinoPanel.setBackground(BG_PANEL);
        destinoPanel.add(rbEnvioTodos);
        destinoPanel.add(rbEnvioDirigido);

        JPanel headerStack = new JPanel();
        headerStack.setLayout(new BoxLayout(headerStack, BoxLayout.Y_AXIS));
        headerStack.setBackground(BG_PANEL);
        headerStack.add(header);
        headerStack.add(destinoPanel);

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

        panel.add(headerStack, BorderLayout.NORTH);
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
        JLabel lbl = new JLabel("Clientes Conectados");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);

        JButton btnRefreshClientes = createStyledButton("Refrescar", ACCENT);
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
        JLabel lbl = new JLabel("Documentos en el Servidor");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setBackground(BG_PANEL);

        JButton btnRefreshDocs = createStyledButton("Refrescar", ACCENT);
        JButton btnDescargarOrig = createStyledButton("Original", SUCCESS);
        JButton btnDescargarHash = createStyledButton("Hash", new Color(249, 226, 175));
        JButton btnDescargarEnc = createStyledButton("Encriptado", new Color(203, 166, 247));

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

    private JPanel createDocumentosPrivadosPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        JLabel lbl = new JLabel("Documentos privados (solo para ti)");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setBackground(BG_PANEL);
        JButton btnRef = createStyledButton("Refrescar", ACCENT);
        JButton btnOrig = createStyledButton("Descargar original", SUCCESS);
        JButton btnHash = createStyledButton("Hash", new Color(249, 226, 175));
        btnRef.addActionListener(e -> refrescarDocumentosPrivados());
        btnOrig.addActionListener(e -> descargarPrivadoSeleccionado("ORIGINAL"));
        btnHash.addActionListener(e -> descargarPrivadoSeleccionado("HASH"));
        btnPanel.add(btnRef);
        btnPanel.add(btnOrig);
        btnPanel.add(btnHash);

        header.add(lbl, BorderLayout.WEST);
        header.add(btnPanel, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(tblDocumentosPrivados);
        scroll.setBorder(createRoundedBorder());
        styleScrollBar(scroll);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createServidoresPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        JLabel lbl = new JLabel("Servidores online (LAN)");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btns.setBackground(BG_PANEL);
        JButton btnRefresh = createStyledButton("Refrescar", ACCENT);
        JButton btnUsar = createStyledButton("Usar seleccionado", SUCCESS);
        btnRefresh.addActionListener(e -> refrescarServidores());
        btnUsar.addActionListener(e -> usarServidorSeleccionado());
        btns.add(btnRefresh);
        btns.add(btnUsar);

        header.add(lbl, BorderLayout.WEST);
        header.add(btns, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(tblServidores);
        scroll.setBorder(createRoundedBorder());
        styleScrollBar(scroll);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createEventosPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        JLabel lbl = new JLabel("Eventos del servidor");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);
        JButton btn = createStyledButton("Refrescar", ACCENT);
        btn.addActionListener(e -> refrescarEventos());
        header.add(lbl, BorderLayout.WEST);
        header.add(btn, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(tblEventos);
        scroll.setBorder(createRoundedBorder());
        styleScrollBar(scroll);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        JLabel lbl = new JLabel("Logs persistidos (BD)");
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);
        JButton btn = createStyledButton("Refrescar", ACCENT);
        btn.addActionListener(e -> refrescarLogs());
        header.add(lbl, BorderLayout.WEST);
        header.add(btn, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(tblLogs);
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
                if (discoveryService != null) discoveryService.stop();
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

    private void ajustarPuertoPorProtocolo() {
        String port = txtPort.getText().trim();
        if (rbUdp.isSelected() && "9000".equals(port)) {
            txtPort.setText("9001");
        } else if (rbTcp.isSelected() && "9001".equals(port)) {
            txtPort.setText("9000");
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
        String nombreCliente = txtNombre.getText().trim().isEmpty()
                ? detectarNombreLocal() : txtNombre.getText().trim();

        CompletableFuture.supplyAsync(() -> {
            try {
                NetworkClient client = new NetworkClient(host, port, proto);
                client.conectar();

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
                btnConectar.setText("Desconectar");
                btnConectar.setEnabled(true);
                lblEstado.setText("● Conectado (" + proto + ")");
                lblEstado.setForeground(SUCCESS);
                appendChat("[SISTEMA] ¡Conectado exitosamente!", SUCCESS);

                setInputsEnabled(false);

                // Enviar nombre de forma asíncrona y no bloqueante DESPUÉS
                // de que los refrescos iniciales hayan podido arrancar.
                CompletableFuture.runAsync(() -> {
                    try { client.setNombre(nombreCliente); } catch (Exception ignored) { }
                });

                // Auto-refresh
                refrescarClientes();
                refrescarDocumentos();
                refrescarDocumentosPrivados();
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
        btnConectar.setText("Conectar");
        lblEstado.setText("● Desconectado");
        lblEstado.setForeground(ERROR_COLOR);
        setInputsEnabled(true);
        appendChat("[SISTEMA] Desconectado del servidor.", TEXT_SECONDARY);
    }

    /**
     * Etiqueta legible del destino de envío para el chat (todos vs cliente / peer).
     */
    private static String formatDestinoLegible(NetworkClient.DestinoEnvio d) {
        if (d == null || d.todos) {
            return "Todos (catálogo público)";
        }
        String proto = d.destProtocolo != null ? d.destProtocolo : "";
        String base = d.destIp + ":" + d.destPuerto + " " + proto.trim();
        if (d.destServidor != null && !d.destServidor.isBlank()
                && !"local".equalsIgnoreCase(d.destServidor.trim())) {
            String pid = d.destServidor.trim();
            String shortPeer = pid.length() > 8 ? pid.substring(0, 8) + "..." : pid;
            return base + " @peer=" + shortPeer;
        }
        return base;
    }

    private NetworkClient.DestinoEnvio leerDestinoEnvio() {
        if (rbEnvioTodos.isSelected()) {
            return NetworkClient.DestinoEnvio.todos();
        }
        int row = tblClientes.getSelectedRow();
        if (row < 0) {
            return null;
        }
        String ip = modelClientes.getValueAt(row, 1).toString();
        int port = modelClientes.getValueAt(row, 2) instanceof Number
                ? ((Number) modelClientes.getValueAt(row, 2)).intValue()
                : Integer.parseInt(modelClientes.getValueAt(row, 2).toString());
        String proto = modelClientes.getValueAt(row, 3).toString();
        Object pid = modelClientes.getValueAt(row, 6);
        String peerId = pid != null ? pid.toString().trim() : "";
        if (peerId.isEmpty()) {
            peerId = "local";
        }
        return NetworkClient.DestinoEnvio.cliente(ip, port, proto, peerId);
    }

    private void enviarMensaje() {
        if (networkClient == null || !networkClient.isConnected()) {
            showError("No estás conectado al servidor");
            return;
        }

        String texto = txtMensaje.getText().trim();
        if (texto.isEmpty() || texto.equals("Escribe un mensaje...")) return;

        if (rbEnvioDirigido.isSelected()) {
            if (tblClientes.getSelectedRow() < 0) {
                showError("Selecciona un cliente en la pestaña Clientes o elige \"Enviar a todos\".");
                return;
            }
        }
        NetworkClient.DestinoEnvio destino = leerDestinoEnvio();
        if (destino == null) {
            showError("Selecciona un cliente en la pestaña Clientes.");
            return;
        }

        txtMensaje.setText("");
        String destEtq = formatDestinoLegible(destino);
        appendChat("[TÚ → " + destEtq + "] " + texto, new Color(166, 227, 161));

        CompletableFuture.supplyAsync(() -> {
            try {
                return networkClient.enviarMensaje(texto, destino);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            SwingUtilities.invokeLater(() -> {
                String hash = resp.getString("hash");
                appendChat("  -> Hash: " + hash, TEXT_SECONDARY);

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

        NetworkClient.DestinoEnvio baseDest = leerDestinoEnvio();
        if (rbEnvioDirigido.isSelected() && baseDest == null) {
            showError("Selecciona un cliente en la pestaña Clientes o elige \"Enviar a todos\".");
            return;
        }
        if (baseDest == null) {
            baseDest = NetworkClient.DestinoEnvio.todos();
        }

        String destEtq = formatDestinoLegible(baseDest);
        appendChat("[SISTEMA] Enviando " + files.length + " archivo(s) → " + destEtq, TEXT_SECONDARY);

        progressBar.setVisible(true);
        progressBar.setValue(0);

        for (File file : files) {
            long fileSize = file.length();
            appendChat("  [ENVIO → " + destEtq + "] " + file.getName() + " (" + formatSize(fileSize) + ")", ACCENT);

            networkClient.enviarArchivo(file, bytesEnviados -> {
                SwingUtilities.invokeLater(() -> {
                    int pct = (int) ((bytesEnviados * 100) / fileSize);
                    progressBar.setValue(pct);
                    lblProgress.setText(formatSize(bytesEnviados) + " / " + formatSize(fileSize));
                });
            }, baseDest).thenAccept(resp -> {
                SwingUtilities.invokeLater(() -> {
                    String hash = resp.getString("hash");
                    appendChat("  [OK] " + file.getName() + " enviado. Hash: " + hash, SUCCESS);
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
                    appendChat("  [ERROR] Error enviando " + file.getName() + ": " + ex.getCause().getMessage(), ERROR_COLOR);
                    progressBar.setVisible(false);
                });
                return null;
            });
        }
    }

    private void refrescarServidores() {
        // El Timer de Swing ya ejecuta en el EDT, pero protegemos por si el
        // metodo se llama desde otro hilo en el futuro.
        Runnable update = () -> {
            if (discoveryService == null) return;
            List<DiscoveredServer> online = discoveryService.servidoresOnline();
            modelServidores.setRowCount(0);
            for (DiscoveredServer s : online) {
                modelServidores.addRow(new Object[]{
                        s.getNombre(), s.getHost(), s.getPuertoTcp(),
                        s.getPuertoUdp(), s.getPuertoPeer(),
                        s.shortId(),
                        s.getUltimaSenal().toString()
                });
            }
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    private void usarServidorSeleccionado() {
        int row = tblServidores.getSelectedRow();
        if (row == -1) {
            showError("Selecciona un servidor de la tabla");
            return;
        }
        String nombre = modelServidores.getValueAt(row, 0).toString();
        String host = modelServidores.getValueAt(row, 1).toString();
        int puertoTcp = ((Number) modelServidores.getValueAt(row, 2)).intValue();
        int puertoUdp = ((Number) modelServidores.getValueAt(row, 3)).intValue();
        txtHost.setText(host);
        txtPort.setText(String.valueOf(rbTcp.isSelected() ? puertoTcp : puertoUdp));
        appendChat("[SISTEMA] Servidor '" + nombre + "' (" + host + ") cargado en el formulario. Pulsa 'Conectar'.",
                TEXT_SECONDARY);
    }

    private void refrescarEventos() {
        if (networkClient == null || !networkClient.isConnected()) {
            showError("Conectate primero a un servidor");
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try { return networkClient.obtenerEventos(100); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).thenAccept(resp -> SwingUtilities.invokeLater(() -> {
            modelEventos.setRowCount(0);
            String json = resp.getString("eventos");
            if (json == null) return;
            try {
                Gson gson = new Gson();
                java.lang.reflect.Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> evs = gson.fromJson(json, (java.lang.reflect.Type) listType);
                for (Map<String, Object> e : evs) {
                    String ts = String.valueOf(e.getOrDefault("timestamp", ""));
                    String hora = ts.contains("T") ? ts.substring(ts.indexOf('T') + 1).replaceAll("\\..*", "") : ts;
                    String ref = e.get("nombreCliente") != null ? e.get("nombreCliente").toString()
                            : (e.get("cliente") != null ? e.get("cliente").toString()
                            : (e.get("origen") != null ? e.get("origen").toString() : ""));
                    String servidor = e.getOrDefault("servidor", "Local").toString();
                    String puerto = e.get("clientePuerto") != null ? e.get("clientePuerto").toString() : "";
                    String protocolo = e.get("clienteProtocolo") != null ? e.get("clienteProtocolo").toString() : "";
                    modelEventos.addRow(new Object[]{
                            hora,
                            servidor,
                            e.getOrDefault("tipo", ""),
                            ref,
                            puerto,
                            protocolo,
                            e.getOrDefault("detalle", "")
                    });
                }
            } catch (Exception e) {
                appendChat("[ERROR] Parseando eventos: " + e.getMessage(), ERROR_COLOR);
            }
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
            return null;
        });
    }

    private void refrescarLogs() {
        if (networkClient == null || !networkClient.isConnected()) {
            showError("Conectate primero a un servidor");
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try { return networkClient.obtenerLogs(100); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).thenAccept(resp -> SwingUtilities.invokeLater(() -> {
            modelLogs.setRowCount(0);
            String json = resp.getString("logs");
            if (json == null) return;
            try {
                Gson gson = new Gson();
                java.lang.reflect.Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> logs = gson.fromJson(json, (java.lang.reflect.Type) listType);
                for (Map<String, Object> l : logs) {
                    String clienteLog = l.get("nombreCliente") != null
                            ? l.get("nombreCliente").toString()
                            : l.getOrDefault("ip", "").toString();
                    String servidor = l.getOrDefault("servidor", "Local").toString();
                    modelLogs.addRow(new Object[]{
                            l.getOrDefault("fecha", ""),
                            servidor,
                            l.getOrDefault("accion", ""),
                            clienteLog,
                            l.getOrDefault("detalle", "")
                    });
                }
            } catch (Exception e) {
                appendChat("[ERROR] Parseando logs: " + e.getMessage(), ERROR_COLOR);
            }
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
            return null;
        });
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
                            String nombreC = (String) c.getOrDefault("nombre", "");
                            String peerId = c.get("peerId") != null ? c.get("peerId").toString() : "";
                            modelClientes.addRow(new Object[]{
                                    nombreC != null && !nombreC.isBlank() ? nombreC : c.get("ip"),
                                    c.get("ip"),
                                    c.get("puerto") instanceof Number ?
                                            ((Number) c.get("puerto")).intValue() : c.get("puerto"),
                                    c.get("protocolo"),
                                    c.get("fechaInicio"),
                                    c.getOrDefault("servidor", "local"),
                                    peerId
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
                            String servidor = d.get("servidor") != null ? d.get("servidor").toString() : "local";
                            String origen = d.get("origen") != null ? d.get("origen").toString() : "local";
                            String valorServidor = "remoto".equalsIgnoreCase(origen) ? servidor : "local";
                            String propietario = d.get("nombrePropietario") != null
                                    ? d.get("nombrePropietario").toString()
                                    : (d.get("ip") != null ? d.get("ip").toString() : "");
                            modelDocumentos.addRow(new Object[]{
                                    id,
                                    d.get("nombre"),
                                    d.get("extension"),
                                    formatSize(tamano),
                                    d.get("tipo"),
                                    d.get("hash"),
                                    propietario,
                                    valorServidor
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

    private void refrescarDocumentosPrivados() {
        if (networkClient == null || !networkClient.isConnected()) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                return networkClient.listarDocumentosPrivados();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            SwingUtilities.invokeLater(() -> {
                modelDocumentosPrivados.setRowCount(0);
                String docsJson = resp.getString("documentos");
                if (docsJson != null) {
                    try {
                        Gson gson = new Gson();
                        java.lang.reflect.Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                        List<Map<String, Object>> docs = gson.fromJson(docsJson, (java.lang.reflect.Type) listType);
                        for (Map<String, Object> d : docs) {
                            long id = d.get("id") instanceof Number ?
                                    ((Number) d.get("id")).longValue() : 0;
                            String servidor = d.get("servidor") != null ? d.get("servidor").toString() : "local";
                            modelDocumentosPrivados.addRow(new Object[]{
                                    id,
                                    d.getOrDefault("resumen", d.get("nombre")),
                                    d.getOrDefault("remitente", ""),
                                    d.getOrDefault("destinatario", "—"),
                                    d.getOrDefault("origenServidorEtiqueta", ""),
                                    d.get("fecha"),
                                    d.get("tipo"),
                                    servidor
                            });
                        }
                    } catch (Exception e) {
                        appendChat("[ERROR] Parseando documentos privados: " + e.getMessage(), ERROR_COLOR);
                    }
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
            return null;
        });
    }

    private void descargarPrivadoSeleccionado(String tipo) {
        if (networkClient == null || !networkClient.isConnected()) {
            showError("No estás conectado al servidor");
            return;
        }
        int row = tblDocumentosPrivados.getSelectedRow();
        if (row < 0) {
            showError("Selecciona un documento privado");
            return;
        }
        Object idObj = modelDocumentosPrivados.getValueAt(row, 0);
        long docId = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(idObj.toString());
        String nombre = modelDocumentosPrivados.getValueAt(row, 1).toString();
        Object servObj = modelDocumentosPrivados.getValueAt(row, 7);
        String servidor = servObj == null ? null : servObj.toString();
        if (servidor != null && servidor.equalsIgnoreCase("local")) {
            servidor = null;
        }
        final String servidorFinal = servidor;

        if ("HASH".equals(tipo)) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return networkClient.descargarHash(docId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenAccept(resp -> {
                SwingUtilities.invokeLater(() -> {
                    String hash = resp.getString("hash");
                    appendChat("[HASH privado] " + nombre + ": " + hash, new Color(249, 226, 175));
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() ->
                        appendChat("[ERROR] " + ex.getCause().getMessage(), ERROR_COLOR));
                return null;
            });
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(nombre));
        fc.setDialogTitle("Guardar documento privado como...");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File destino = fc.getSelectedFile();

        progressBar.setVisible(true);
        progressBar.setValue(0);
        appendChat("  [DESCARGA privada] " + nombre + "...", ACCENT);

        CompletableFuture.runAsync(() -> {
            try {
                networkClient.descargarArchivo(docId, servidorFinal, destino, bytesRecibidos -> {
                    SwingUtilities.invokeLater(() -> {
                        lblProgress.setText(formatSize(bytesRecibidos) + " recibidos");
                    });
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                appendChat("  [OK] Descarga privada: " + destino.getName(), SUCCESS);
                progressBar.setValue(100);
                lblProgress.setText("Completado");
                Timer timer = new Timer(2000, evt -> {
                    progressBar.setVisible(false);
                    lblProgress.setText(" ");
                });
                timer.setRepeats(false);
                timer.start();
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Throwable root = unwrapThrowable(ex);
                if (root instanceof NetworkClient.DownloadQueuedException) {
                    appendChat("  [COLA] " + root.getMessage(), new Color(249, 226, 175));
                } else {
                    appendChat("  [ERROR] Descarga privada: " + root.getMessage(), ERROR_COLOR);
                }
                progressBar.setVisible(false);
            });
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
        Object servObj = modelDocumentos.getValueAt(row, 7);
        String servidor = servObj == null ? null : servObj.toString();
        if (servidor != null && servidor.equalsIgnoreCase("local")) servidor = null;
        final String servidorFinal = servidor;

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
                    appendChat("  -> Hash copiado al portapapeles", TEXT_SECONDARY);
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
        appendChat("  [DESCARGA] Descargando " + tipo.toLowerCase() + ": " + nombre + "...", ACCENT);

        CompletableFuture.runAsync(() -> {
            try {
                if ("ORIGINAL".equals(tipo)) {
                    networkClient.descargarArchivo(docId, servidorFinal, destino, bytesRecibidos -> {
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
                appendChat("  [OK] Descarga completada: " + destino.getName(), SUCCESS);
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
                Throwable root = unwrapThrowable(ex);
                if (root instanceof NetworkClient.DownloadQueuedException) {
                    appendChat("  [COLA] " + root.getMessage(), new Color(249, 226, 175));
                } else {
                    appendChat("  [ERROR] Error descargando: " + root.getMessage(), ERROR_COLOR);
                }
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

    private Throwable unwrapThrowable(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current != null ? current : new RuntimeException("Error desconocido");
    }

    private void setInputsEnabled(boolean enabled) {
        txtHost.setEnabled(enabled);
        txtPort.setEnabled(enabled);
        txtNombre.setEnabled(enabled);
        rbTcp.setEnabled(enabled);
        rbUdp.setEnabled(enabled);
        // Radios de destino (todos / dirigido): siempre habilitados para poder
        // cambiar el modo de envio con la sesion activa sin reconectar.
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

    private static String detectarNombreLocal() {
        try {
            String h = java.net.InetAddress.getLocalHost().getHostName();
            if (h != null && !h.isBlank()) return h;
        } catch (Exception ignored) { }
        return System.getProperty("user.name", "cliente");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
