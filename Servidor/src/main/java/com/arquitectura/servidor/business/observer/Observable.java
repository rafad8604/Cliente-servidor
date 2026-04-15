package com.arquitectura.servidor.business.observer;

import com.arquitectura.servidor.business.activity.ActivityEvent;

public interface Observable {

    void addObserver(Observer observer);

    void removeObserver(Observer observer);

    void notifyObservers(ActivityEvent event);
}

