# Sistema P2P de Mensajería y Archivos

Proyecto Java multi-módulo. Varios servidores se descubren entre sí por UDP broadcast en la LAN y comparten el **catálogo público** (solo documentos con alcance «todos»). Un cliente conectado a cualquier servidor lista y descarga esos archivos (proxy si el origen es otro nodo). Además hay **envío dirigido** (mensaje o archivo a un cliente concreto), **documentos privados** (solo visibles para destinatario y remitente en el servidor donde quedaron) y **relay entre servidores** (`PEER_ENTREGAR_*`) cuando el destino está en otro peer. El cliente descubre servidores por broadcast (no necesita IP de antemano).

## Escenario típico

```
LAN
├── PC1  servidor + cliente
├── PC2  cliente   (se conecta a cualquier servidor que ve)
├── PC3  servidor + cliente
└── PC4  servidor + cliente
```

- Los **servidores** se anuncian cada 5 s por UDP broadcast (`9200`). Cualquier nodo que escucha ese puerto los detecta.
- Los **clientes** también escuchan ese broadcast → pestaña *"Servidores"* del GUI muestra la lista en vivo (`nombre · host · puertoTCP · puertoUDP`). Botón *"Usar seleccionado"* rellena el formulario.
- Cada cliente se conecta a **un** servidor (TCP o UDP). Al listar documentos ve solo los **públicos** (locales + remotos vía caché); los **privados** se consultan aparte. La descarga remota sigue siendo proxy automático. El envío puede ser a **todos** o a **un cliente** (pestaña *Clientes*; campo `peerId` para enviar vía relay a otro servidor).

## Arquitectura

```
┌──────────┐         ┌─────────────┐                      ┌─────────────┐
│ Cliente  │  TCP/   │  Servidor A │   TCP peer-to-peer   │  Servidor B │
│ (Swing)  │◀──UDP──▶│             │◀────────────────────▶│             │
│          │  9000/  │  TCP 9000   │   puerto 9100        │             │
│  o HTTP  │  9001/  │  UDP 9001   │   discovery UDP 9200 │             │
│          │  8080   │  HTTP 8080  │   (broadcast)        │             │
└──────────┘         └──────┬──────┘                      └──────┬──────┘
                            │                                    │
                       MySQL :33306                          MySQL :33306
                       (BD local A)                          (BD local B)
```

- **`shared/`** — `Comando`, `Mensaje` (Gson JSON línea-delimitado), `CryptoUtil` (AES-256, SHA-256, PBKDF2).
- **`server/`**
  - `net/` — TCP (`ServerCore`, `ClientHandler`, `TcpClientChannel`) y UDP (`UdpHandler`, `UdpClientChannel`) con fragmentación automática de mensajes grandes.
  - `peer/` — `PeerDiscoveryService` (broadcast UDP), `PeerRegistry` (TTL), `PeerServer/PeerSession` (TCP entre servidores: listados, descarga, `PEER_ENTREGAR_MENSAJE` / `PEER_ENTREGAR_ARCHIVO`), `PeerClient`, `PeerCatalog` (caché TTL de docs remotos **solo públicos**), `PeerProxyService` (proxy de descarga + relay de subida dirigida).
  - `service/` — `DocumentoService` (cifrado, hash, chunks 50 MB; metadatos de alcance `TODOS`/`DIRIGIDO`, destino y remitente), `LogService`.
  - `dao/` — JDBC con pool de 10 conexiones.
  - `http/` — `HttpGateway` (REST + UI estática).
  - `events/` — bus de eventos asíncrono + consola formateada + `InMemoryEventBuffer` (últimos 500).
- **`client/`** — `NetworkClient` (TCP/UDP, reensamblaje de fragmentos, envío a todos o dirigido con `destServidor`), `ClientDiscoveryService` (escucha broadcast UDP `9200`), GUI Swing + FlatLaf con pestañas, H2 local para historial.

## Cómo funcionan los datos (BD + catálogo)

**Cada servidor tiene su propia MySQL local independiente.** No hay BD compartida, no se replica nada. Se comparte solo **metadata** entre servidores; los **bytes** del archivo viajan únicamente al momento de descargar (proxy).

```
┌────────────────────────────────────────────────────────────────┐
│ Servidor A (PC1)                  Servidor B (PC3)             │
│                                                                │
│  ┌────────────────┐                 ┌────────────────┐         │
│  │ MySQL local A  │                 │ MySQL local B  │         │
│  │ documentos     │                 │ documentos     │         │
│  │ chunks (AES)   │                 │ chunks (AES)   │         │
│  │ logs, clientes │                 │ logs, clientes │         │
│  └───────▲────────┘                 └───────▲────────┘         │
│          │                                  │                  │
│          │ lectura local                    │                  │
│          │                                  │                  │
│  ┌───────┴────────┐  PEER_LISTAR_DOCS       ┌────────┴───────┐ │
│  │ PeerCatalog A  │◀────────cada 10s───────▶│ PeerCatalog B  │ │
│  │ (cache TTL 30s)│  solo públicos (JSON)     │ (cache TTL 30s)│ │
│  │ • docs de B    │                         │ • docs de A    │ │
│  └────────────────┘                         └────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### Qué guarda cada nodo

| Cosa                          | Dónde vive                       | Quién la tiene                       |
|-------------------------------|----------------------------------|--------------------------------------|
| Bytes del archivo (cifrados)  | tabla `documentos_chunks` MySQL  | **solo el servidor donde se subió**  |
| Metadatos (id, nombre, hash, alcance, destino, remitente, origen) | tabla `documentos` MySQL         | **solo el servidor donde se subió o recibió por relay**  |
| Catálogo de docs de otros     | RAM (`PeerCatalog`), TTL 30 s    | **todos los servidores** (solo **públicos** de otros nodos)   |
| Lista de servidores online    | RAM (`PeerRegistry`), TTL 15 s   | **todos los servidores**             |
| Logs de acciones              | tabla `logs` MySQL               | **el servidor donde ocurrió**        |
| Clientes conectados           | tabla `clientes_conectados`      | **el servidor al que están**         |

### Flujo de descubrimiento

```
  Cada 5 s                         Cada 5 s                  Cada 5 s
     │                                │                         │
┌────▼────┐  PEER_HELLO          ┌────▼────┐              ┌─────▼────┐
│ Server A│ ───broadcast UDP───▶ │ Server B│              │ Server C │
│         │ ◀───broadcast UDP─── │         │  ───────────▶│          │
└─────────┘  255.255.255.255:9200└─────────┘              └──────────┘
     ▲                                                          │
     │                                                          │
     └────────────  PeerRegistry ◀────────  hello ───────────────┘
                    (id, nombre, host, puerto, lastSeen)
                    TTL 15 s sin hello → marca offline

  Cliente abre socket en 9200 con SO_REUSEADDR y captura los mismos
  HELLO. Mantiene su propia tabla y la muestra en pestaña "Servidores".
```

### Flujo de listar documentos

```
1. Cliente conectado a Server A pide:
   ──LISTAR_DOCUMENTOS──▶ Server A

2. Server A construye respuesta:
   docs_locales = documentos con envio_alcance = TODOS   (MySQL local de A)
   docs_remotos = PeerCatalog (solo entradas públicas de otros nodos)
   respuesta = docs_locales ⊕ docs_remotos

   Los envíos DIRIGIDO no entran en este listado; el cliente usa
   LISTAR_DOCUMENTOS_PRIVADOS en el servidor correspondiente.

3. Cliente recibe tabla unificada:
   ┌────┬──────────┬──────┬─────────┬────────┬─────────────────┐
   │ ID │ Nombre   │ Tam  │ Hash    │ Origen │ Servidor        │
   ├────┼──────────┼──────┼─────────┼────────┼─────────────────┤
   │ 1  │ a.pdf    │ 2 MB │ 8a3f... │ local  │ local           │
   │ 5  │ foto.jpg │ 1 MB │ 9b7c... │ remoto │ b3c4d5e6 (PC3)  │
   └────┴──────────┴──────┴─────────┴────────┴─────────────────┘
```

El `PeerCatalog` se refresca solo cada 10 s en background — el listado siempre es instantáneo.

### Flujo de descarga (local vs proxy)

```
Caso 1: archivo LOCAL al servidor que atiende
─────────────────────────────────────────────
Cliente ──DESCARGAR_ARCHIVO {id:1, servidor:"local"}──▶ Server A
                                                          │
                            comprueba acceso (público o privado si eres destinatario/remitente)
                            MySQL A: SELECT chunks       │
                            descifra AES                  │
Cliente ◀──────────── bytes ──────────────────────────────┘


Caso 2: archivo REMOTO (proxy)
──────────────────────────────
Cliente ──DESCARGAR_ARCHIVO {id:5, servidor:"b3c4d5e6..."}──▶ Server A
                                                                 │
                                  busca peerId en PeerRegistry  │
                                  → IP y puerto de Server B      │
                                                                 │
                       Server A ──PEER_DESCARGAR_ARCHIVO──▶ Server B
                                                              │
                                          MySQL B: SELECT chunks
                                          descifra AES
                       Server A ◀────── bytes ──────────────────┘
                                  │
Cliente ◀──────────── bytes ──────┘   (reenvío sin guardar copia)
```

- El cliente nunca habla directamente con B. El servidor A actúa como proxy.
- A **no guarda copia** de lo que pasa por proxy: cada descarga vuelve a viajar B→A→cliente.
- La descarga deja un evento `PEER_DESCARGA_PROXY` en A (entrante) y en B (saliente).

### Consecuencias prácticas

- Si subes a A y **A se cae**, ese archivo deja de ser descargable hasta que A vuelva.
- El catálogo es **eventualmente consistente**: tras subir a A, B lo verá en ≤10 s.
- **No se duplican bytes** en disco entre nodos, no se sincroniza nada pesado.
- Los **DIRIGIDO** no entran en el catálogo público cruzado: cada uno vive solo en la MySQL del servidor que lo almacenó (local o tras relay).
- Logs y `clientes_conectados` son **por nodo** (cada `OBTENER_LOGS` muestra solo el servidor al que estás conectado).
- Esquema `documentos` en [`server/src/main/resources/init.sql`](server/src/main/resources/init.sql): `envio_alcance` (`TODOS`|`DIRIGIDO`), `dest_*`, `origen_servidor_etiqueta`, `origen_peer_id`, snapshot de remitente (`remitente_*`). En BD ya existente, las columnas se añaden con `CALL agregar_columna_si_no_existe(...)`.

### Envío dirigido y relay

- **Cliente → servidor local:** `ENVIAR_MENSAJE` / cabecera `ENVIAR_ARCHIVO` (TCP o UDP) con campos opcionales `envioAlcance`, `destIp`, `destPuerto`, `destProtocolo`, `destServidor` (`local` o UUID del peer). Por defecto sigue siendo público (`TODOS`).
- **Cliente → otro servidor:** el servidor origen resuelve el peer y usa `PeerClient` para `PEER_ENTREGAR_*`; el receptor persiste como `DIRIGIDO` con metadatos de origen.
- **Privados en GUI:** bandeja por refresco (`LISTAR_DOCUMENTOS_PRIVADOS`); no hay push TCP de chat entre clientes.

## Protocolo

- Control: JSON línea-delimitado con `{comando, datos, timestamp}`.
- Comandos relevantes: `LISTAR_DOCUMENTOS` (públicos), `LISTAR_DOCUMENTOS_PRIVADOS`, `LISTAR_CLIENTES` (incluye `peerId` por fila para `destServidor`), `ENVIAR_MENSAJE` / `ENVIAR_ARCHIVO` con destino opcional, `PEER_ENTREGAR_MENSAJE` / `PEER_ENTREGAR_ARCHIVO` entre servidores.
- Streams binarios: bytes crudos después del header JSON (TCP) o fragmentados en datagramas tipo `DATOS` + `FIN` (UDP).
- UDP usa datagramas de **8 KB** (compatible con `net.inet.udp.maxdgram` por defecto en macOS). Mensajes JSON más grandes se fragmentan en `CONTROL_FRAG` + `CONTROL_END` y se reensamblan en el receptor.

## Cliente GUI

Pestañas en el panel derecho:
- **Servidores** — descubrimiento UDP en vivo (auto-refresh cada 3 s + botón). Muestra `Nombre · Host · TCP · UDP · Peer · ID · Última señal`. Botón *"Usar seleccionado"* autocompleta host/puerto.
- **Clientes** — clientes conectados al servidor actual (y fusionados remotos cuando aplica). Incluye columna **PeerId** para elegir destino en envíos dirigidos o relay.
- **Documentos** — solo **públicos** (locales y remotos). Columna *"Servidor"* indica `local` o `<peerId>`. Al descargar un remoto, el servidor actual hace proxy.
- **Docs. privados** — mensajes/archivos `DIRIGIDO` donde eres destinatario o remitente en ese servidor: columnas resumen, remitente, **destinatario**, origen, fecha, tipo, servidor; refrescar y descargar como en documentos públicos.
- **Eventos** — eventos en memoria del servidor (botón refrescar).
- **Logs (BD)** — acciones persistidas (conexiones, uploads, descargas).

**Chat (panel izquierdo):** radios *Enviar a todos* / *Cliente seleccionado* — se pueden cambiar **con la sesión conectada** (no bloquean con el resto del formulario de conexión). Los mensajes y adjuntos se anotan en el chat con el destino legible, p. ej. `[TÚ → Todos (catálogo público)]` o `[TÚ → ip:puerto PROTO @peer=…]`.

## Puertos

| Servicio        | Puerto | Protocolo            |
|-----------------|--------|----------------------|
| Cliente TCP     | 9000   | TCP                  |
| Cliente UDP     | 9001   | UDP                  |
| Panel web       | 8080   | HTTP                 |
| Peer-to-peer    | 9100   | TCP entre servidores |
| Discovery       | 9200   | UDP broadcast        |
| MySQL           | 33306  | TCP                  |

## Requisitos

- Java 17+
- Docker + Docker Compose (para MySQL)
- Maven (opcional)

## Levantamiento

```bash
# 1. MySQL
docker compose up -d

# 2. Compilar
mvn -DskipTests package

# 3. Servidor (un nodo)
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" com.app.server.ServerApp

# 4. Cliente
java -cp "client/target/client-1.0-SNAPSHOT.jar:client/target/libs/*" com.app.client.ClientApp
```

### Múltiples servidores (P2P)

**Mismo PC** (pruebas) — segundo nodo con puertos y nombre distintos:

```bash
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" \
  com.app.server.ServerApp \
  --nombre=PC-SalaB --tcp=9010 --udp=9011 --http=8081 --peer=9110
```

**PCs distintos en la misma LAN** — en cada PC:

1. `docker compose up -d` (cada PC con su propia MySQL local).
2. Asegurar que el firewall permite TCP `9000/9100/8080` y UDP `9001/9200` en la interfaz LAN.
3. Levantar el servidor (opcionalmente con nombre legible):

```bash
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" \
  com.app.server.ServerApp --nombre=PC-Sala-A
```

Si omites `--nombre`, se usa el hostname del SO. El nombre se ve en `peers`, `/api/peers`, panel web y tabla *"Servidores"* del cliente GUI.

El servidor detecta automáticamente su IP LAN (IPv4 no-loopback) y la anuncia. Si tienes varias interfaces y quieres forzar una IP específica, usa `--host=192.168.1.50`. Los nodos se descubren solos por broadcast UDP en `9200`. Flag `--peers=off` desactiva P2P.

Al iniciar, el servidor imprime las dos URLs del panel web:
```
[HTTP] Interfaz web disponible en:
       http://localhost:8080
       http://192.168.1.50:8080  (LAN)
```

## Flags CLI del servidor

| Flag                | Default | Descripción                                  |
|---------------------|---------|----------------------------------------------|
| `--tcp=N`           | 9000    | puerto TCP de clientes                       |
| `--udp=N`           | 9001    | puerto UDP de clientes                       |
| `--http=N`          | 8080    | puerto del panel web                         |
| `--peer=N`          | 9100    | puerto TCP entre servidores                  |
| `--discovery=N`     | 9200    | puerto UDP broadcast de descubrimiento       |
| `--max=N`           | 10      | clientes máximos por pool                    |
| `--host=IP`         | auto    | IP a anunciar (si hay varias interfaces)     |
| `--nombre=texto`    | hostname| alias legible del servidor (visible en GUIs) |
| `--peers=off`       | on      | desactiva el módulo P2P                      |

## Comandos del servidor (consola)

| Comando         | Descripción                                  |
|-----------------|----------------------------------------------|
| `status`        | clientes activos en pool TCP/UDP             |
| `peers`         | peers en línea con nombre, host, puertos     |
| `remotos`       | documentos publicados por peers              |
| `events [N]`    | últimos N eventos del buffer (default 20)    |
| `events on/off` | activa/desactiva log de eventos en consola   |
| `logs [N]`      | últimos N logs de BD (default 20)            |
| `exit`          | apaga el servidor                            |

## API HTTP

| Método | Endpoint                       | Notas                                    |
|--------|--------------------------------|------------------------------------------|
| GET    | `/api/health`                  | estado del gateway                       |
| POST   | `/api/connect?port=8080`       | abre sesión, devuelve `sessionId`        |
| POST   | `/api/disconnect`              | header `X-Session-Id`                    |
| GET    | `/api/documentos`              | lista (locales + remotos)                |
| GET    | `/api/clientes`                | clientes conectados                      |
| GET    | `/api/peers`                   | peers en línea con `nombre`              |
| GET    | `/api/events?limit=100`        | eventos recientes del bus (memoria)      |
| GET    | `/api/logs?limit=50`           | últimos logs de BD                       |
| POST   | `/api/upload?filename=x.pdf`   | subir archivo (`X-Session-Id`, body bin) |
| GET    | `/api/download?documentoId=1&tipo=ORIGINAL` | `tipo`: ORIGINAL, HASH, ENCRIPTADO |
| POST   | `/api/chat`                    | `{texto}` (`X-Session-Id`)               |
| GET    | `/api/chat`                    | historial                                |

## Logs y eventos

Cuatro lugares para verlos:

1. **Archivo** — `storage/server-logs/server-YYYYMMDD-HHmmss.log` (uno por arranque).
2. **Consola del servidor** — formato `[HH:mm:ss] [CAT] TIPO | detalles`. Categorías: `SYS`, `NET`, `POOL`, `STOR`, `MSG`, `PEER`, `ERR`. Comandos `events [N]` y `logs [N]` imprimen los últimos N en cualquier momento.
3. **Panel web** — pestañas *"Eventos"* (memoria, refresh 2 s) y *"Logs (BD)"*.
4. **Cliente GUI** — pestañas *"Eventos"* y *"Logs (BD)"* del servidor al que estás conectado, con botón refrescar.

Buffer en memoria de los últimos 500 eventos (no requiere BD). `/api/logs` y la pestaña *Logs (BD)* muestran los registros de acciones persistidos en MySQL (conexiones, uploads, descargas), distintos de los eventos en vivo.

## Seguridad

- **AES-256/CBC + PBKDF2** para cifrar archivos en BD.
- **SHA-256** para integridad.
- Tamaño máximo de archivo: **1 GB** (validado en stream).
- `sanitizeFileName` rechaza paths con `..` o relativos.

## Tests

```bash
mvn test
```

La suite `mvn test` incluye unitarios y pruebas livianas de red; los tests de integración con MySQL/HTTP real que estén `@Disabled` se ejecutan manualmente.

Cobertura por módulo (resumen):
- `shared` — `ComandoTest`, `MensajeTest`, `CryptoUtilTest`.
- `server` — `CommandDispatcherTest`, `UdpFragmentationTest`, `PeerRegistryTest`, `PeerInfoTest`, `PeerPingIntegrationTest`, `PeerRelayIntegrationTest` (relay mínimo `PEER_ENTREGAR_MENSAJE`), `InMemoryEventBufferTest`, `ClientPoolTest`, `ServerModelsTest`.
- `client` — `NetworkClientTcpTest`, `NetworkClientUdpTest`, `ClientDiscoveryServiceTest`, `HistorialDocumentoTest`.

## Notas

- MySQL debe estar arriba antes del servidor.
- Liberar puertos: `9000`, `9001`, `8080`, `9100`, `9200`, `33306`.
- Para multi-PC en LAN: cada PC con su propio Docker MySQL; el broadcast UDP debe pasar el firewall.
- El cliente GUI muestra documentos **públicos** locales y remotos en la misma tabla (columna `Servidor`); los privados van en *Docs. privados*.
- Descarga encriptada de peers remotos no soportada (requeriría compartir clave AES entre nodos).
- El canal peer entre servidores es TCP en claro en LAN (TLS queda como mejora futura).
- Si un servidor se cae, sus documentos dejan de ser descargables hasta que vuelva (no hay replicación).
