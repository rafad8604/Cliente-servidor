package com.app.server.net;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Dispatcher común (independiente del protocolo) para comandos que no
 * requieren flujo binario bidireccional (LISTAR_*, ENVIAR_MENSAJE,
 * DESCARGAR_HASH).
 *
 * Permite que {@link ClientHandler} (TCP) y {@link UdpHandler} (UDP) compartan
 * la misma lógica de negocio a través de un {@link ClientChannel}.
 */
public class CommandDispatcher {

    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private final ServerEventBus eventBus;

    public CommandDispatcher(DocumentoService documentoService,
                             LogService logService,
                             ClienteConectadoDAO clienteDAO,
                             ServerEventBus eventBus) {
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = clienteDAO;
        this.eventBus = eventBus;
    }

    /**
     * Intenta despachar un comando "simple" (sin stream).
     * @return true si fue manejado, false si el comando requiere tratamiento
     * específico por protocolo (ENVIAR_ARCHIVO, DESCARGAR_ARCHIVO...).
     */
    public boolean dispatchSimple(ClientChannel channel, Mensaje msg) throws Exception {
        Comando cmd = msg.getComando();
        if (cmd == null) return false;

        switch (cmd) {
            case ENVIAR_MENSAJE: {
                String texto = msg.getString("texto");
                if (texto == null || texto.isEmpty()) {
                    channel.sendMensaje(Mensaje.error("Mensaje vacío"));
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
                List<Documento> docs = documentoService.listarDocumentos();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < docs.size(); i++) {
                    Documento d = docs.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format(
                            "{\"id\":%d,\"nombre\":\"%s\",\"extension\":\"%s\",\"tamano\":%d,\"tipo\":\"%s\",\"hash\":\"%s\",\"ip\":\"%s\",\"fecha\":\"%s\"}",
                            d.getId(),
                            escapeJson(d.getNombre()),
                            escapeJson(d.getExtension() != null ? d.getExtension() : ""),
                            d.getTamano(),
                            d.getTipo().name(),
                            d.getHashSha256(),
                            d.getIpPropietario(),
                            d.getFechaCreacion().toString()));
                }
                sb.append("]");
                channel.sendMensaje(Mensaje.respuestaOk("documentos", sb.toString())
                        .put("total", docs.size()));
                return true;
            }
            case LISTAR_CLIENTES: {
                List<ClienteConectado> clientes = clienteDAO.listarTodos();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < clientes.size(); i++) {
                    ClienteConectado c = clientes.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format(
                            "{\"ip\":\"%s\",\"puerto\":%d,\"protocolo\":\"%s\",\"fechaInicio\":\"%s\"}",
                            escapeJson(c.getIp()),
                            c.getPuerto(),
                            escapeJson(c.getProtocolo()),
                            c.getFechaInicio().toString()));
                }
                sb.append("]");
                channel.sendMensaje(Mensaje.respuestaOk("clientes", sb.toString())
                        .put("total", clientes.size()));
                return true;
            }
            case DESCARGAR_HASH: {
                long docId = msg.getLong("documentoId");
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

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
