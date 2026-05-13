package com.app.server.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utilidades de red para descubrir interfaces y direcciones de broadcast LAN.
 *
 * <p>En Windows con Docker / Hyper-V / VirtualBox / VPN suele haber varias
 * interfaces "up" no-loopback que NO son la LAN real (p.ej. {@code
 * vEthernet (DockerNAT)}, {@code vEthernet (Default Switch)}, {@code VirtualBox
 * Host-Only}, etc.). Si se anuncia la IP de una de esas interfaces, las otras
 * maquinas LAN nunca podran contactarla. Igualmente, un broadcast a {@code
 * 255.255.255.255} solo sale por la interfaz de la ruta por defecto, que en
 * Windows muchas veces es una interfaz virtual. Estas utilidades atacan ambos
 * problemas:</p>
 *
 * <ul>
 *   <li>{@link #detectarIpLan()} elige la mejor IPv4 LAN ignorando loopback,
 *       link-local, virtuales y nombres tipicos de adaptadores virtuales.</li>
 *   <li>{@link #direccionesBroadcast()} enumera la direccion de broadcast de
 *       cada {@code InterfaceAddress} de cada interfaz UP no-loopback, para
 *       enviar PEER_HELLO por todas ellas.</li>
 * </ul>
 */
public final class NetworkUtils {

    /** Fragmentos de nombre que descartamos para evitar interfaces virtuales / NAT. */
    private static final String[] NOMBRES_FILTRADOS = {
            "docker", "veth", "vethernet", "virtualbox", "vmware",
            "hyper-v", "wsl", "tap", "tun", "ppp", "vpn",
            "bluetooth", "loopback"
    };

    private NetworkUtils() { }

    /**
     * Devuelve la mejor IPv4 LAN detectada. Prioriza interfaces fisicas reales
     * en rangos privados (192.168/16 &gt; 10/8 &gt; 172.16-31/12) y descarta
     * interfaces loopback, virtuales, Docker, VPN, etc.
     */
    public static String detectarIpLan() {
        List<Inet4Address> candidatos = enumerarIpv4LAN();
        if (candidatos.isEmpty()) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                return "127.0.0.1";
            }
        }
        // Ya vienen ordenados por preferencia desde enumerarIpv4LAN().
        return candidatos.get(0).getHostAddress();
    }

    /**
     * Enumera todas las IPv4 que parecen ser LAN reales, ordenadas por
     * preferencia. Permite mostrar al usuario que opciones encontro y por que
     * eligio una concreta.
     */
    public static List<Inet4Address> enumerarIpv4LAN() {
        List<Inet4Address> resultado = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!esInterfazAceptable(ni)) continue;

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address &&
                            !addr.isLoopbackAddress() &&
                            !addr.isLinkLocalAddress() &&
                            !addr.isMulticastAddress() &&
                            !addr.isAnyLocalAddress()) {
                        resultado.add((Inet4Address) addr);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[NET] Error enumerando interfaces: " + e.getMessage());
        }
        resultado.sort((a, b) -> Integer.compare(rangoPreferencia(b), rangoPreferencia(a)));
        return resultado;
    }

    /**
     * Direcciones de broadcast (una por cada InterfaceAddress) de las
     * interfaces LAN aceptables. Incluye 255.255.255.255 como ultimo recurso
     * por si alguna red lo enruta. Se devuelve una coleccion sin duplicados.
     */
    public static List<InetAddress> direccionesBroadcast() {
        Set<InetAddress> set = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!esInterfazAceptable(ni)) continue;

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress bcast = ia.getBroadcast();
                    if (bcast != null) set.add(bcast);
                }
            }
        } catch (Exception e) {
            System.err.println("[NET] Error enumerando broadcast: " + e.getMessage());
        }
        try {
            // Fallback universal: 255.255.255.255 (limited broadcast). En Windows
            // sale solo por la interfaz de ruta por defecto, pero no hace dano.
            set.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) { }
        return new ArrayList<>(set);
    }

    /**
     * Diagnostico legible de todas las interfaces (para logs al iniciar el
     * servidor). Una linea por interfaz: nombre, estado y direcciones IPv4.
     */
    public static List<String> describirInterfaces() {
        List<String> lineas = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                boolean ok;
                String motivo;
                try {
                    if (!ni.isUp()) { ok = false; motivo = "down"; }
                    else if (ni.isLoopback()) { ok = false; motivo = "loopback"; }
                    else if (ni.isVirtual()) { ok = false; motivo = "subinterfaz"; }
                    else if (esNombreFiltrado(ni)) { ok = false; motivo = "filtrado por nombre"; }
                    else { ok = true; motivo = "ok"; }
                } catch (Exception e) {
                    ok = false; motivo = "error: " + e.getMessage();
                }

                StringBuilder sb = new StringBuilder();
                sb.append(ok ? "[+] " : "[-] ");
                sb.append(ni.getName()).append(" (").append(ni.getDisplayName()).append(") ")
                        .append(motivo);

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                List<String> ipv4 = new ArrayList<>();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) ipv4.add(a.getHostAddress());
                }
                if (!ipv4.isEmpty()) sb.append("  ipv4=").append(ipv4);
                lineas.add(sb.toString());
            }
        } catch (Exception e) {
            lineas.add("Error enumerando: " + e.getMessage());
        }
        if (lineas.isEmpty()) lineas.add("(sin interfaces)");
        return Collections.unmodifiableList(lineas);
    }

    private static boolean esInterfazAceptable(NetworkInterface ni) {
        try {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) return false;
            return !esNombreFiltrado(ni);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean esNombreFiltrado(NetworkInterface ni) {
        String nombre = (ni.getName() + " " + ni.getDisplayName())
                .toLowerCase(Locale.ROOT);
        for (String frag : NOMBRES_FILTRADOS) {
            if (nombre.contains(frag)) return true;
        }
        return false;
    }

    /**
     * Puntaje para ordenar las IPv4 candidatas. Mas alto = mas probable que sea
     * la LAN real. 192.168/16 es la mas comun en casa/oficina; 10/8 segundo;
     * 172.16-31/12 tercero (y muchas veces es Docker, por eso menos puntaje).
     */
    private static int rangoPreferencia(Inet4Address addr) {
        byte[] b = addr.getAddress();
        int b0 = b[0] & 0xFF;
        int b1 = b[1] & 0xFF;
        if (b0 == 192 && b1 == 168) return 30;
        if (b0 == 10) return 20;
        if (b0 == 172 && b1 >= 16 && b1 <= 31) return 10;
        // Cualquier otra (publica o desconocida) ultima.
        return 0;
    }
}
