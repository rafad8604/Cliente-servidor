package com.arquitectura.servidor.business.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:user.properties")
public class UserConnectionConfig {

    @Value("${server.max.users}")
    private int maxUsers;

    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        if (maxUsers <= 0) {
            throw new IllegalArgumentException("maxUsers debe ser mayor a 0");
        }
        this.maxUsers = maxUsers;
    }
}

