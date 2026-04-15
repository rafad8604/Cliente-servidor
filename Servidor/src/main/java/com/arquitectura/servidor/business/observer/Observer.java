package com.arquitectura.servidor.business.observer;

import com.arquitectura.servidor.business.activity.ActivityEvent;

public interface Observer {

    void update(ActivityEvent event);
}

