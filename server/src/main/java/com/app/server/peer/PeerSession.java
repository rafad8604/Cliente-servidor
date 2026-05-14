package com.app.server.peer;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.net.BoundedInputStream;
import com.app.server.net.DocumentoEnvioHelper;
import com.app.server.service.DocumentoService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maneja una conexion entrante de otro peer (servidor).
 *
 * <p>Protocolo: igual al cliente TCP (JSON-linea para control + bytes crudos
 * para stream). Acepta comandos {@code PEER_PING}, {@code PEER_LISTAR_DOCS},
 * {@code PEER_DESCARGAR_ARCHIVO}, {@code PEER_DESCARGAR_HASH}.</p>
 *
 * <p>El peer remoto se identifica via {@code PEER_HELLO} inicial (campo
 * {@code id}) que registramos en el {@link PeerRegistry} (refresca lastSeen).</p>
 */
public class PeerSession implements Runnable {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Socket socket;
    private final PeerRegistry registry;
    private final DocumentoService documentoService;
    private final ClienteConectadoDAO clienteDAO;
    private final ServerEventBus eventBus;
    private String remotePeerId = "desconocido";

    public PeerSession(Socket socket,
                       PeerRegistry registry,
                       DocumentoService documentoService,
                       ClienteConectadoDAO clienteDAO,
                       ServerEventBus eventBus) {
        this.socket = socket;
        this.registry = registry;
        this.documentoService = documentoService;
        this.clienteDAO = clienteDAO;
        this.eventBus = eventBus;
    }

    @Override
    public void run() {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            // Saludo inicial.
            String localId = registry.getLocalId();
            enviarLinea(out, Mensaje.respuestaOk("peerId", localId).toJson());

            while (!socket.isClosed()) {
                String linea = leerLinea(in);
                if (linea == null) break;
                if (linea.isBlank()) continue;

                Mensaje msg;
                try {
                    msg = Mensaje.fromJson(linea);
                } catch (Exception e) {
                    enviarLinea(out, Mensaje.error("JSON invalido: " + e.getMessage()).toJson());
                    continue;
                }
                if (msg.getComando() == null) {
                    enviarLinea(out, Mensaje.error("Comando ausente").toJson());
                    continue;
                }
                if (!atender(msg, in, out)) break;
            }
        } catch (Exception e) {
            if (eventBus != null) eventBus.publishError("peer-session", e, null);
            System.err.println("[PEER] Sesion con " + remotePeerId + " termino con error: " + e.getMessage());
        }
    }

    private boolean atender(Mensaje msg, InputStream in, OutputStream out) throws Exception {
        Comando cmd = msg.getComando();
        switch (cmd) {
            case PEER_HELLO: {
                remotePeerId = msg.getString("id");
                String nombre = msg.getString("nombre");
                int puertoPeer = msg.getInt("puertoPeer");
                int puertoTcp = msg.getInt("puertoTcp");
                int puertoUdp = msg.getInt("puertoUdp");
                String host = msg.getString("host");
                if (host == null) host = socket.getInetAddress().getHostAddress();
                if (remotePeerId != null) {
                    registry.aplicarHello(new PeerInfo(remotePeerId, nombre, host, puertoPeer, puertoTcp, puertoUdp));
                }
                enviarLinea(out, Mensaje.respuestaOk("peerId", registry.getLocalId()).toJson());
                return true;
            }
            case PEER_PING: {
                enviarLinea(out, Mensaje.respuestaOk("pong", true).toJson());
                return true;
            }
            case PEER_LISTAR_CLIENTES: {
                List<Map<String, Object>> rows = new ArrayList<>();
                if (clienteDAO != null) {
                    for (ClienteConectado c : clienteDAO.listarTodos()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("ip", c.getIp());
                        row.put("puerto", c.getPuerto());
                        row.put("protocolo", c.getProtocolo());
                        row.put("fechaInicio", c.getFechaInicio() != null ? c.getFechaInicio().toString() : null);
                        row.put("nombre", c.getNombre() != null ? c.getNombre() : "");
                        row.put("servidor", "local");
                        row.put("peerId", registry.getLocalId());
                        rows.add(row);
                    }
                }
                enviarLinea(out, Mensaje.respuestaOk("clientes", GSON.toJson(rows))
                        .put("total", rows.size()).toJson());
                return true;
            }
            case PEER_LISTAR_DOCS: {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Documento d : documentoService.listarDocumentosPublicos()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", d.getId());
                    row.put("nombre", d.getNombre());
                    row.put("extension", d.getExtension() != null ? d.getExtension() : "");
                    row.put("tamano", d.getTamano());
                    row.put("tipo", d.getTipo() != null ? d.getTipo().name() : null);
                    row.put("hash", d.getHashSha256());
                    row.put("ip", d.getIpPropietario());
                    row.put("fecha", d.getFechaCreacion() != null ? d.getFechaCreacion().toString() : null);
                    rows.add(row);
                }
                enviarLinea(out, Mensaje.respuestaOk("documentos", GSON.toJson(rows))
                        .put("total", rows.size()).toJson());
                return true;
            }
            case PEER_DESCARGAR_HASH: {
                long docId = msg.getLong("documentoId");
                String hash = documentoService.getHash(docId);
                if (hash == null) {
                    enviarLinea(out, Mensaje.error("Documento no encontrado: " + docId).toJson());
                } else {
                    enviarLinea(out, Mensaje.respuestaOk("hash", hash).put("documentoId", docId).toJson());
                }
                return true;
            }
            case PEER_DESCARGAR_ARCHIVO: {
                long docId = msg.getLong("documentoId");
                Documento doc = documentoService.obtenerDocumento(docId);
                if (doc == null) {
                    enviarLinea(out, Mensaje.error("Documento no encontrado: " + docId).toJson());
                    return true;
                }
                Mensaje header = Mensaje.respuestaOk()
                        .put("nombre", doc.getNombre())
                        .put("tamano", doc.getTamano())
                        .put("hash", doc.getHashSha256());
                enviarLinea(out, header.toJson());

                try (InputStream stream = documentoService.getArchivoOriginalStream(docId)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = stream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                }
                if (eventBus != null) {
                    eventBus.publish(ServerEventType.PEER_DESCARGA_PROXY, "peer-session",
                            "peer=" + remotePeerId + " docId=" + docId + " (saliente)");
                }
                return true;
            }
            case PEER_ENTREGAR_MENSAJE: {
                String texto = msg.getString("texto");
                if (texto == null || texto.isBlank()) {
                    enviarLinea(out, Mensaje.error("texto vacio").toJson());
                    return true;
                }
                String ipProp = msg.getString("ipPropietario");
                if (ipProp == null || ipProp.isBlank()) {
                    ipProp = socket.getInetAddress().getHostAddress();
                }
                DocumentoService.DocumentoEnvioParams envio = DocumentoEnvioHelper.buildParamsFromRelay(msg);
                try {
                    Documento doc = documentoService.procesarMensaje(texto, ipProp, envio);
                    enviarLinea(out, Mensaje.respuestaOk("hash", doc.getHashSha256())
                            .put("documentoId", doc.getId())
                            .put("mensaje", "Mensaje relay almacenado").toJson());
                } catch (Exception e) {
                    enviarLinea(out, Mensaje.error("Relay mensaje: " + e.getMessage()).toJson());
                }
                return true;
            }
            case PEER_ENTREGAR_ARCHIVO: {
                String nombre = msg.getString("nombre");
                long tamano = msg.getLong("tamano");
                String ipProp = msg.getString("ipPropietario");
                if (ipProp == null || ipProp.isBlank()) {
                    ipProp = socket.getInetAddress().getHostAddress();
                }
                DocumentoService.DocumentoEnvioParams envio = DocumentoEnvioHelper.buildParamsFromRelay(msg);
                try (InputStream limited = new BoundedInputStream(in, tamano)) {
                    Documento doc = documentoService.procesarArchivo(nombre, tamano, ipProp, limited, envio);
                    enviarLinea(out, Mensaje.respuestaOk("hash", doc.getHashSha256())
                            .put("documentoId", doc.getId())
                            .put("mensaje", "Archivo relay almacenado").toJson());
                } catch (Exception e) {
                    enviarLinea(out, Mensaje.error("Relay archivo: " + e.getMessage()).toJson());
                }
                return true;
            }
            default:
                enviarLinea(out, Mensaje.error("Comando peer no soportado: " + cmd).toJson());
                return true;
        }
    }

    private static String leerLinea(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (buf.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static void enviarLinea(OutputStream out, String json) throws java.io.IOException {
        out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
