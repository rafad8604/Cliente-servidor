package com.arquitectura.servidor.business.user;

public interface ObjectPool<T> {

    T borrowObject();

    void returnObject(T object);

    int availableCount();

    int inUseCount();
}

