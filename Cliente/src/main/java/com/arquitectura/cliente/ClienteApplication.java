package com.arquitectura.cliente;

import com.arquitectura.cliente.presentation.swing.DesktopMainFrame;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import javax.swing.SwingUtilities;

@SpringBootApplication
public class ClienteApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication app = new SpringApplication(ClienteApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Bean
    @ConditionalOnBean(DesktopMainFrame.class)
    ApplicationRunner launchDesktopUi(DesktopMainFrame frame) {
        return args -> SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }
}
