package com.app.server.net;

import com.app.server.models.Documento;
import com.app.server.peer.PeerRegistry;
import com.app.server.service.DocumentoService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

/**
 * Construye {@link com.app.server.service.DocumentoService.DocumentoEnvioParams} desde JSON de cliente
 * y ayuda a decidir si el destino es un peer remoto.
 */
public final class DocumentoEnvioHelper {

    private DocumentoEnvioHelper() {
    }

    public static boolean esPeerRemoto(String destServidor, PeerRegistry registry) {
        if (destServidor == null || destServidor.isBlank()) {
            return false;
        }
        if ("local".equalsIgnoreCase(destServidor.trim())) {
            return false;
        }
        if (registry == null) {
            return false;
        }
        return !destServidor.trim().equals(registry.getLocalId());
    }

    /**
     * Construye parametros para persistencia local. Si {@code envioAlcance} es DIRIGIDO,
     * exige destIp, destPuerto y destProtocolo en el mensaje.
     */
    public static DocumentoService.DocumentoEnvioParams buildLocalParams(
            Mensaje msg,
            ClientContext ctx,
            String remitenteNombre) {

        DocumentoService.DocumentoEnvioParams p = new DocumentoService.DocumentoEnvioParams();
        p.setRemitentePuerto(ctx.getPort());
        p.setRemitenteProtocolo(ctx.getProtocol());
        p.setRemitenteNombre(remitenteNombre != null ? remitenteNombre : "");
        p.setOrigenServidorEtiqueta("local");
        p.setOrigenPeerId(null);

        String alc = msg.getString("envioAlcance");
        Documento.EnvioAlcance alcance = Documento.EnvioAlcance.TODOS;
        if (alc != null && !alc.isBlank()) {
            try {
                alcance = Documento.EnvioAlcance.valueOf(alc.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                alcance = Documento.EnvioAlcance.TODOS;
            }
        }
        p.setAlcance(alcance);

        if (alcance == Documento.EnvioAlcance.DIRIGIDO) {
            p.setDestIp(msg.getString("destIp"));
            if (msg.getDatos().containsKey("destPuerto")) {
                p.setDestPuerto(((Number) msg.getDatos().get("destPuerto")).intValue());
            }
            p.setDestProtocolo(msg.getString("destProtocolo"));
        }
        return p;
    }

    public static Mensaje buildRelayMensajePayload(
            String texto,
            Mensaje original,
            ClientContext ctx,
            String remitenteNombre,
            String origenEtiqueta,
            String origenPeerId) {

        Mensaje m = new Mensaje(Comando.PEER_ENTREGAR_MENSAJE);
        m.put("texto", texto);
        m.put("destIp", original.getString("destIp"));
        if (original.getDatos().containsKey("destPuerto")) {
            m.put("destPuerto", original.getDatos().get("destPuerto"));
        }
        m.put("destProtocolo", original.getString("destProtocolo"));
        m.put("ipPropietario", ctx.getIp());
        m.put("remitentePuerto", ctx.getPort());
        m.put("remitenteProtocolo", ctx.getProtocol());
        m.put("remitenteNombre", remitenteNombre != null ? remitenteNombre : "");
        m.put("origenServidorEtiqueta", origenEtiqueta != null ? origenEtiqueta : "");
        m.put("origenPeerId", origenPeerId != null ? origenPeerId : "");
        return m;
    }

    public static Mensaje buildRelayArchivoHeader(
            Mensaje original,
            String nombre,
            long tamano,
            ClientContext ctx,
            String remitenteNombre,
            String origenEtiqueta,
            String origenPeerId) {

        Mensaje m = new Mensaje(Comando.PEER_ENTREGAR_ARCHIVO);
        m.put("nombre", nombre);
        m.put("tamano", tamano);
        m.put("destIp", original.getString("destIp"));
        if (original.getDatos().containsKey("destPuerto")) {
            m.put("destPuerto", original.getDatos().get("destPuerto"));
        }
        m.put("destProtocolo", original.getString("destProtocolo"));
        m.put("ipPropietario", ctx.getIp());
        m.put("remitentePuerto", ctx.getPort());
        m.put("remitenteProtocolo", ctx.getProtocol());
        m.put("remitenteNombre", remitenteNombre != null ? remitenteNombre : "");
        m.put("origenServidorEtiqueta", origenEtiqueta != null ? origenEtiqueta : "");
        m.put("origenPeerId", origenPeerId != null ? origenPeerId : "");
        return m;
    }

    public static DocumentoService.DocumentoEnvioParams buildParamsFromRelay(Mensaje msg) {
        DocumentoService.DocumentoEnvioParams p = new DocumentoService.DocumentoEnvioParams();
        p.setAlcance(Documento.EnvioAlcance.DIRIGIDO);
        p.setDestIp(msg.getString("destIp"));
        if (msg.getDatos().containsKey("destPuerto")) {
            p.setDestPuerto(((Number) msg.getDatos().get("destPuerto")).intValue());
        }
        p.setDestProtocolo(msg.getString("destProtocolo"));
        p.setRemitenteNombre(msg.getString("remitenteNombre"));
        if (msg.getDatos().containsKey("remitentePuerto")) {
            p.setRemitentePuerto(((Number) msg.getDatos().get("remitentePuerto")).intValue());
        }
        p.setRemitenteProtocolo(msg.getString("remitenteProtocolo"));
        p.setOrigenServidorEtiqueta(msg.getString("origenServidorEtiqueta"));
        p.setOrigenPeerId(msg.getString("origenPeerId"));
        return p;
    }
}
