package com.arquitectura.cliente;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "cliente.ui.enabled=false")
class ClienteApplicationTests {

    @Test
    void contextLoads() {
    }
}
