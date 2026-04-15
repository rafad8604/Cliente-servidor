package com.arquitectura.servidor.presentation.console;

import com.arquitectura.servidor.business.user.UserConnectionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class UserConnectionDemoRunner implements CommandLineRunner {

    private final UserConnectionService userConnectionService;

    public UserConnectionDemoRunner(UserConnectionService userConnectionService) {
        this.userConnectionService = userConnectionService;
    }

    @Override
    public void run(String... args) throws Exception {
        // Simulamos conectar usuarios de prueba
        try {
            userConnectionService.connect("Alice", "192.168.1.10");
            userConnectionService.connect("Bob", "192.168.1.11");
            userConnectionService.connect("Charlie", "192.168.1.12");
            userConnectionService.connect("Diana", "192.168.1.13");
            userConnectionService.connect("Eve", "192.168.1.14");
            // Este debe fallar
            userConnectionService.connect("Frank", "192.168.1.15");
        } catch (Exception e) {
            // Excepcion esperada para Frank
        }

        // Desconectamos uno y probamos agregar otro
        if (!userConnectionService.getActiveConnections().isEmpty()) {
            String userId = userConnectionService.getActiveConnections().get(0).userId();
            userConnectionService.disconnect(userId);

            try {
                userConnectionService.connect("Frank", "192.168.1.15");
            } catch (Exception e) {
                // No debería ocurrir aquí
            }
        }
    }
}

