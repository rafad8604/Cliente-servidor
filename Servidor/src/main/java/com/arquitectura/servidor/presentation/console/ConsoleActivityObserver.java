package com.arquitectura.servidor.presentation.console;

import com.arquitectura.servidor.business.activity.ActivityEvent;
import com.arquitectura.servidor.business.observer.Observer;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ConsoleActivityObserver implements Observer {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void update(ActivityEvent event) {
        String timestamp = FORMATTER.format(event.timestamp().atZone(ZoneId.systemDefault()));
        System.out.printf("[%s] [%s] [%s] %s%n", timestamp, event.source(), event.type(), event.message());
    }
}

