package com.app.server.net;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.dao.LogDAO;
import com.app.server.events.InMemoryEventBuffer;
import com.app.server.events.ServerEvent;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.models.Log;
import com.app.server.peer.PeerCatalog;
import com.app.server.peer.PeerClient;
import com.app.server.peer.PeerInfo;
import com.app.server.peer.PeerRegistry;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatcher comun (independiente del protocolo) para comandos que no
 * requieren flujo binario bidireccional (LISTAR_*, ENVIAR_MENSAJE,
 * DESCARGAR_HASH, LISTAR_SERVIDORES).
 *
 * Permite que {@link ClientHandler} (TCP) y {@link UdpHandler} (UDP) compartan
 * la misma logica de negocio a traves de un {@link ClientChannel}.
 */
public class CommandDispatcher {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private final ServerEventBus eventBus;
    private final PeerRegistry peerRegistry;
    private final PeerCatalog peerCatalog;
    private final PeerClient peerClient;
    private final InMemoryEventBuffer eventBuffer;
    private final LogDAO logDAO;
    private final ClientNameCache nameCache;

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus) {
        this(documentoService, logService, clienteDAO, eventBus, null, null, null, null, null, null);
    }

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus,
                             PeerRegistry peerRegistry,
                             PeerCatalog peerCatalog) {
        this(documentoService, logService, clienteDAO, eventBus, peerRegistry, peerCatalog, null, null, null, null);
    }

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus,
                             PeerRegistry peerRegistry,
                             PeerCatalog peerCatalog,
                             InMemoryEventBuffer eventBuffer,
                             LogDAO logDAO) {
        this(documentoService, logService, clienteDAO, eventBus, peerRegistry, peerCatalog, eventBuffer, logDAO, null, null);
    }

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus,
                             PeerRegistry peerRegistry,
                             PeerCatalog peerCatalog,
                             InMemoryEventBuffer eventBuffer,
                             LogDAO logDAO,
                             PeerClient peerClient) {
        this(documentoService, logService, clienteDAO, eventBus, peerRegistry, peerCatalog, eventBuffer, logDAO, peerClient, null);
    }

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus,
                             PeerRegistry peerRegistry,
                             PeerCatalog peerCatalog,
                             InMemoryEventBuffer eventBuffer,
                             LogDAO logDAO,
                             PeerClient peerClient,
                             ClientNameCache nameCache) {
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = clienteDAO;
        this.eventBus = eventBus;
        this.peerRegistry = peerRegistry;
        this.peerCatalog = peerCatalog;
        this.peerClient = peerClient;
        this.eventBuffer = eventBuffer;
        this.logDAO = logDAO;
        this.nameCache = nameCache;
    }

    /**
     * Intenta despachar un comando "simple" (sin stream).
     * @return true si fue manejado, false si el comando requiere tratamiento
     * especifico por protocolo (ENVIAR_ARCHIVO, DESCARGAR_ARCHIVO...).
     */
    public boolean dispatchSimple(ClientChannel channel, Mensaje msg) throws Exception {
        Comando cmd = msg.getComando();
        if (cmd == null) {
            channel.sendMensaje(Mensaje.error("Comando ausente o nulo"));
            return true;
        }

        switch (cmd) {
            case ENVIAR_MENSAJE: {
                String texto = msg.getString("texto");
                if (texto == null || texto.isEmpty()) {
                    channel.sendMensaje(Mensaje.error("Mensaje vacio"));
                    return true;
                }
                Documento doc = documentoService.procesarMensaje(texto, channel.getContext().getIp());
                logService.logMensajeRecibido(channel.getContext().getIp());
                if (eventBus != null) {
                    eventBus.publish(ServerEventType.MENSAJE_RECIBIDO,
                            channel.getContext(),
                            "docId=" + doc.getId());
                }
                channel.sendMensaje(Mensaje.respuestaOk("hash", doc.getHashSha256())
                        .put("documentoId", doc.getId())
                        .put("mensaje", "Mensaje recibido y procesado"));
                return true;
            }
            case LISTAR_DOCUMENTOS: {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Documento d : documentoService.listarDocumentos()) {
                    Map<String, Object> row = documentoToRow(d, "local", null);
                    if (nameCache != null) row.put("nombrePropietario", nameCache.getOrIp(d.getIpPropietario()));
                    rows.add(row);
                }
                if (peerCatalog != null) {
                    for (PeerCatalog.RemoteDocumento rd : peerCatalog.listarRemotos()) {
                        rows.add(remotoToRow(rd));
                    }
                }
                channel.sendMensaje(Mensaje.respuestaOk("documentos", GSON.toJson(rows))
                        .put("total", rows.size()));
                return true;
            }
            case SET_NOMBRE: {
                String nombre = msg.getString("nombre");
                if (nombre == null || nombre.isBlank()) {
                    channel.sendMensaje(Mensaje.error("Parametro 'nombre' requerido"));
                    return true;
                }
                String nombreTrimmed = nombre.trim();
                try {
                    clienteDAO.actualizarNombre(channel.getContext().getIp(),
                            channel.getContext().getPort(), nombreTrimmed);
                } catch (Exception e) {
                    // no bloquear si falla la BD
                }
                if (nameCache != null) nameCache.set(channel.getContext().getIp(), nombreTrimmed);
                channel.sendMensaje(Mensaje.respuestaOk("nombre", nombreTrimmed));
                return true;
            }
            case LISTAR_CLIENTES: {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (ClienteConectado c : clienteDAO.listarTodos()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ip", c.getIp());
                    row.put("puerto", c.getPuerto());
                    row.put("protocolo", c.getProtocolo());
                    row.put("fechaInicio", c.getFechaInicio() != null ? c.getFechaInicio().toString() : null);
                    row.put("nombre", c.getNombre() != null ? c.getNombre() : "");
                    row.put("servidor", "local");
                    rows.add(row);
                }
                if (peerRegistry != null && peerClient != null) {
                    Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                    for (PeerInfo peer : peerRegistry.listarOnline()) {
                        try {
                            Mensaje resp = peerClient.listarClientes(peer);
                            if (resp.getComando() == Comando.RESPUESTA) {
                                String json = resp.getString("clientes");
                                if (json != null && !json.isBlank()) {
                                    List<Map<String, Object>> peerRows = GSON.fromJson(json, listType);
                                    String label = peer.getNombre() + " (" + peer.getId().substring(0, 8) + ")";
                                    for (Map<String, Object> row : peerRows) {
                                        row.put("servidor", label);
                                        rows.add(row);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // peer no disponible, se omite
                        }
                    }
                }
                channel.sendMensaje(Mensaje.respuestaOk("clientes", GSON.toJson(rows))
                        .put("total", rows.size()));
                return true;
            }
            case LISTAR_SERVIDORES: {
                List<Map<String, Object>> rows = new ArrayList<>();
                if (peerRegistry != null) {
                    for (PeerInfo p : peerRegistry.listarOnline()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", p.getId());
                        row.put("nombre", p.getNombre());
                        row.put("host", p.getHost());
                        row.put("puertoPeer", p.getPuertoPeer());
                        row.put("puertoTcp", p.getPuertoTcp());
                        row.put("puertoUdp", p.getPuertoUdp());
                        row.put("ultimaSenal", p.getUltimaSenal().toString());
                        rows.add(row);
                    }
                }
                channel.sendMensaje(Mensaje.respuestaOk("servidores", GSON.toJson(rows))
                        .put("total", rows.size()));
                return true;
            }
            case OBTENER_EVENTOS: {
                int limit = msg.getDatos().containsKey("limit") ? msg.getInt("limit") : 100;
                List<Map<String, Object>> rows = new ArrayList<>();
                if (eventBuffer != null) {
                    for (ServerEvent ev : eventBuffer.snapshot(limit)) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("timestamp", ev.getTimestamp().toString());
                        row.put("tipo", ev.getTipo().name());
                        row.put("origen", ev.getOrigen());
                        row.put("detalle", ev.getDetalle());
                        String clienteStr = ev.getClientContext() != null
                                ? ev.getClientContext().toString() : null;
                        row.put("cliente", clienteStr);
                        if (nameCache != null && ev.getClientContext() != null) {
                            row.put("nombreCliente", nameCache.getOrIp(ev.getClientContext().getIp()));
                        }
                        rows.add(row);
                    }
                }
                channel.sendMensaje(Mensaje.respuestaOk("eventos", GSON.toJson(rows))
                        .put("total", rows.size()));
                return true;
            }
            case OBTENER_LOGS: {
                int limit = msg.getDatos().containsKey("limit") ? msg.getInt("limit") : 50;
                List<Map<String, Object>> rows = new ArrayList<>();
                if (logDAO != null) {
                    for (Log l : logDAO.listarUltimos(limit)) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", l.getId());
                        row.put("accion", l.getAccion());
                        row.put("ip", l.getIpOrigen());
                        if (nameCache != null) row.put("nombreCliente", nameCache.getOrIp(l.getIpOrigen()));
                        row.put("fecha", l.getFechaHora() != null ? l.getFechaHora().toString() : null);
                        row.put("detalle", l.getDetalles());
                        rows.add(row);
                    }
                }
                channel.sendMensaje(Mensaje.respuestaOk("logs", GSON.toJson(rows))
                        .put("total", rows.size()));
                return true;
            }
            case DESCARGAR_HASH: {
                if (!msg.getDatos().containsKey("documentoId")) {
                    channel.sendMensaje(Mensaje.error("Parametro 'documentoId' requerido"));
                    return true;
                }
                long docId;
                try {
                    docId = msg.getLong("documentoId");
                } catch (Exception e) {
                    channel.sendMensaje(Mensaje.error("Parametro 'documentoId' invalido"));
                    return true;
                }
                String hash = documentoService.getHash(docId);
                if (hash == null) {
                    channel.sendMensaje(Mensaje.error("Documento no encontrado: " + docId));
                    return true;
                }
                logService.logDescarga(channel.getContext().getIp(), "doc-" + docId, "HASH");
                byte[] hashBytes = hash.getBytes(StandardCharsets.UTF_8);
                channel.sendMensaje(Mensaje.respuestaOk("hash", hash)
                        .put("tamano", hashBytes.length)
                        .put("tipoDescarga", "HASH"));
                return true;
            }
            default:
                return false;
        }
    }

    private Map<String, Object> documentoToRow(Documento d, String origen, String peerId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("nombre", d.getNombre());
        row.put("extension", d.getExtension() != null ? d.getExtension() : "");
        row.put("tamano", d.getTamano());
        row.put("tipo", d.getTipo() != null ? d.getTipo().name() : null);
        row.put("hash", d.getHashSha256());
        row.put("ip", d.getIpPropietario());
        row.put("fecha", d.getFechaCreacion() != null ? d.getFechaCreacion().toString() : null);
        row.put("origen", origen);
        row.put("servidor", peerId == null ? "local" : peerId);
        return row;
    }

    private Map<String, Object> remotoToRow(PeerCatalog.RemoteDocumento rd) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rd.getId());
        row.put("nombre", rd.getNombre());
        row.put("extension", rd.getExtension());
        row.put("tamano", rd.getTamano());
        row.put("tipo", rd.getTipo());
        row.put("hash", rd.getHash());
        row.put("ip", rd.getIp());
        row.put("fecha", rd.getFecha());
        row.put("origen", "remoto");
        row.put("servidor", rd.getPeerId());
        return row;
    }
}
