package com.app.client;

import com.app.client.dao.H2Connection;
import com.app.client.gui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Punto de entrada del cliente.
 * Inicializa FlatLaf, H2 y muestra la GUI.
 */
public class ClientApp {

    public static void main(String[] args) {
        // 1. Configurar Look & Feel (FlatLaf Dark)
        try {
            FlatDarkLaf.setup();
            UIManager.put("Component.arc", 8);
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);
        } catch (Exception e) {
            System.err.println("[UI] FlatLaf no disponible, usando Nimbus...");
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ex) {
                // usar default
            }
        }

        // 2. Inicializar base de datos H2
        try {
            H2Connection.getInstance().init();
        } catch (Exception e) {
            System.err.println("[ERROR] No se pudo inicializar H2: " + e.getMessage());
            JOptionPane.showMessageDialog(null,
                    "Error inicializando base de datos local:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        // 3. Mostrar GUI
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
