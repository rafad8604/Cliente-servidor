package com.arquitectura.servidor.business.activity;

import com.arquitectura.servidor.business.observer.Observable;
import com.arquitectura.servidor.business.observer.Observer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ServerActivitySource implements Observable {

    private final List<Observer> observers = new CopyOnWriteArrayList<>();

    @Override
    public void addObserver(Observer observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(ActivityEvent event) {
        for (Observer observer : observers) {
            observer.update(event);
        }
    }

    public void emitActivity(String type, String message) {
        ActivityEvent event = new ActivityEvent(java.time.Instant.now(), "Servidor", type, message);
        notifyObservers(event);
    }
}

