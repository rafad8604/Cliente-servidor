package com.app.server.net;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache en memoria que mapea IP de cliente a su nombre legible.
 * Se actualiza cuando el cliente envía SET_NOMBRE y persiste mientras
 * el servidor esté corriendo. Compartida entre todos los handlers.
 */
public class ClientNameCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public void set(String ip, String nombre) {
        if (ip != null && nombre != null && !nombre.isBlank()) {
            cache.put(ip, nombre.trim());
        }
    }

    /** Devuelve el nombre si existe, o la IP tal cual si no hay nombre registrado. */
    public String getOrIp(String ip) {
        if (ip == null) return "";
        String nombre = cache.get(ip);
        return (nombre != null && !nombre.isBlank()) ? nombre + " (" + ip + ")" : ip;
    }

    public boolean has(String ip) {
        return ip != null && cache.containsKey(ip);
    }
}
