package com.app.shared.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper JSON para todos los mensajes del protocolo.
 * Se serializa/deserializa como una línea JSON terminada en \n.
 */
public class Mensaje {

    private static final Gson GSON = new GsonBuilder().create();

    private Comando comando;
    private Map<String, Object> datos;
    private String timestamp;

    public Mensaje() {
        this.datos = new HashMap<>();
        this.timestamp = LocalDateTime.now().toString();
    }

    public Mensaje(Comando comando) {
        this();
        this.comando = comando;
    }

    public Mensaje(Comando comando, Map<String, Object> datos) {
        this();
        this.comando = comando;
        this.datos = datos != null ? datos : new HashMap<>();
    }

    // --- Getters y Setters ---

    public Comando getComando() {
        return comando;
    }

    public void setComando(Comando comando) {
        this.comando = comando;
    }

    public Map<String, Object> getDatos() {
        return datos;
    }

    public void setDatos(Map<String, Object> datos) {
        this.datos = datos;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    // --- Métodos de ayuda para datos ---

    public Mensaje put(String key, Object value) {
        this.datos.put(key, value);
        return this;
    }

    public String getString(String key) {
        Object val = datos.get(key);
        return val != null ? val.toString() : null;
    }

    public long getLong(String key) {
        Object val = datos.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.parseLong(val.toString());
    }

    public int getInt(String key) {
        Object val = datos.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return Integer.parseInt(val.toString());
    }

    public boolean getBoolean(String key) {
        Object val = datos.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return Boolean.parseBoolean(val.toString());
    }

    // --- Serialización ---

    /**
     * Serializa este mensaje a JSON (una sola línea).
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Deserializa un JSON string a Mensaje.
     */
    public static Mensaje fromJson(String json) {
        Mensaje msg = GSON.fromJson(json, Mensaje.class);
        if (msg.datos == null) {
            msg.datos = new HashMap<>();
        }
        return msg;
    }

    /**
     * Helper para crear un mensaje de respuesta exitosa.
     */
    public static Mensaje respuestaOk() {
        return new Mensaje(Comando.RESPUESTA).put("status", "OK");
    }

    /**
     * Helper para crear un mensaje de respuesta con datos.
     */
    public static Mensaje respuestaOk(String key, Object value) {
        return respuestaOk().put(key, value);
    }

    /**
     * Helper para crear un mensaje de error.
     */
    public static Mensaje error(String detalle) {
        return new Mensaje(Comando.ERROR).put("status", "ERROR").put("detalle", detalle);
    }

    @Override
    public String toString() {
        return "Mensaje{" + "comando=" + comando + ", datos=" + datos + '}';
    }
}
