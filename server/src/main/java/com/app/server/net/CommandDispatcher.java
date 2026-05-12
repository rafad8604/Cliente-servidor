package com.app.server.net;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.peer.PeerCatalog;
import com.app.server.peer.PeerInfo;
import com.app.server.peer.PeerRegistry;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus) {
        this(documentoService, logService, clienteDAO, eventBus, null, null);
    }

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus,
                             PeerRegistry peerRegistry,
                             PeerCatalog peerCatalog) {
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = clienteDAO;
        this.eventBus = eventBus;
        this.peerRegistry = peerRegistry;
        this.peerCatalog = peerCatalog;
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
                    rows.add(documentoToRow(d, "local", null));
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
            case LISTAR_CLIENTES: {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (ClienteConectado c : clienteDAO.listarTodos()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ip", c.getIp());
                    row.put("puerto", c.getPuerto());
                    row.put("protocolo", c.getProtocolo());
                    row.put("fechaInicio", c.getFechaInicio() != null ? c.getFechaInicio().toString() : null);
                    rows.add(row);
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
