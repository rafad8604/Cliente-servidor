# Sistema P2P de Mensajería y Archivos

Proyecto Java multi-módulo. Varios servidores se descubren entre sí por UDP broadcast en la LAN y comparten el **catálogo público** (solo documentos con alcance «todos»). Un cliente conectado a cualquier servidor lista y descarga esos archivos (proxy si el origen es otro nodo). Además hay **envío dirigido** (mensaje o archivo a un cliente concreto), **documentos privados** (solo visibles para destinatario y remitente en el servidor donde quedaron) y **relay entre servidores** (`PEER_ENTREGAR_*`) cuando el destino está en otro peer. El cliente descubre servidores por broadcast (no necesita IP de antemano).

El sistema es como un grupo de servidores de archivos en red local que se coordinan solos sin servidor central. Cada servidor es independiente (tiene su propia base de datos), pero se hablan entre sí para que un cliente conectado a cualquiera de ellos pueda ver archivos de todos. La comunicación cliente-servidor usa TCP o UDP; la comunicación servidor-servidor usa un canal TCP aparte.

---

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
- Cada cliente se conecta a **un** servidor (TCP o UDP). Al listar documentos ve los **públicos** (locales + remotos vía caché); los **privados** se consultan aparte. La descarga remota es proxy automático. El envío puede ser a **todos** o a **un cliente** concreto (pestaña *Clientes*; campo `peerId` para enviar vía relay a otro servidor).

---

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

- **`shared/`** — `Comando` (enum de todos los comandos del protocolo), `Mensaje` (JSON línea-delimitado con Gson), `CryptoUtil` (AES-256, SHA-256, PBKDF2).
- **`server/`**
  - `net/` — TCP (`ServerCore`, `ClientHandler`, `TcpClientChannel`, `CommandDispatcher`) y UDP (`UdpHandler`, `UdpClientChannel`) con fragmentación automática de mensajes grandes.
  - `peer/` — `PeerDiscoveryService` (broadcast UDP), `PeerRegistry` (TTL), `PeerServer`/`PeerSession` (TCP entre servidores: listados, descarga, relay, logs, eventos), `PeerClient`, `PeerCatalog` (caché TTL de docs remotos), `PeerProxyService`.
  - `service/` — `DocumentoService` (cifrado, hash, chunks 50 MB; metadatos `TODOS`/`DIRIGIDO`, destino, remitente), `LogService`.
  - `dao/` — JDBC con pool de 10 conexiones.
  - `http/` — `HttpGateway` (REST + UI estática).
  - `events/` — bus de eventos asíncrono + consola formateada + `InMemoryEventBuffer` (últimos 500).
- **`client/`** — `NetworkClient` (TCP/UDP, reensamblaje de fragmentos, envío a todos o dirigido con `destServidor`), `ClientDiscoveryService` (escucha broadcast UDP `9200`), GUI Swing + FlatLaf, H2 local para historial.

---

## Visibilidad cross-servidor: qué ve el cliente de otros nodos

El cliente siempre habla con un solo servidor. Ese servidor es el responsable de ir a buscar datos de los otros y devolverlos consolidados. El cliente no sabe ni le importa que la información vino de otra máquina.

| Vista en el GUI         | Origen de los datos                                        | Cómo se agrega                              |
|-------------------------|-------------------------------------------------------------|---------------------------------------------|
| **Clientes conectados** | Todos los servidores online                                | On-demand: el servidor consulta cada peer en el momento de la petición |
| **Documentos públicos** | Todos los servidores online                                | Caché automática: `PeerCatalog` refresca cada 10 s en background       |
| **Eventos**             | Todos los servidores online                                | On-demand: se consulta cada peer al presionar "Refrescar"               |
| **Logs (BD)**           | Todos los servidores online                                | On-demand: se consulta cada peer al presionar "Refrescar"               |
| **Docs. privados**      | Solo el servidor al que estás conectado                    | Siempre local; los de otros servidores se ven conectándose allí         |

Cada fila de Clientes, Eventos y Logs incluye una columna **Servidor** que indica de qué nodo viene (`Local` o `NombrePeer (id...)`).

---

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
│  ┌───────┴────────┐  PEER_LISTAR_DOCS  ┌────┴───────────┐      │
│  │ PeerCatalog A  │◀────cada 10s──────▶│ PeerCatalog B  │      │
│  │ (cache TTL 30s)│  solo públicos     │ (cache TTL 30s)│      │
│  └────────────────┘                   └────────────────┘      │
└────────────────────────────────────────────────────────────────┘
```

### Qué guarda cada nodo

| Dato                              | Dónde vive                      | Quién lo tiene                                    |
|-----------------------------------|---------------------------------|---------------------------------------------------|
| Bytes del archivo (cifrados)      | `documentos_chunks` MySQL       | Solo el servidor donde se subió                   |
| Metadatos del documento           | `documentos` MySQL              | El servidor donde se subió o recibió por relay    |
| Catálogo de docs de otros nodos   | RAM (`PeerCatalog`), TTL 30 s   | Todos los servidores (solo públicos de otros)     |
| Lista de servidores online        | RAM (`PeerRegistry`), TTL 15 s  | Todos los servidores                              |
| Logs de acciones                  | `logs` MySQL                    | El servidor donde ocurrió la acción               |
| Eventos en vivo                   | RAM (`InMemoryEventBuffer`)     | El servidor donde ocurrió; se consulta on-demand  |
| Clientes conectados               | `clientes_conectados` MySQL     | El servidor al que están conectados               |
| Nombre del cliente                | RAM (`ClientNameCache`)         | El servidor al que están conectados; se pierde al reiniciar |

### Flujo de descubrimiento

```
  Cada 5 s                         Cada 5 s                  Cada 5 s
     │                                │                         │
┌────▼────┐  PEER_HELLO          ┌────▼────┐              ┌─────▼────┐
│ Server A│ ───broadcast UDP───▶ │ Server B│              │ Server C │
│         │ ◀───broadcast UDP─── │         │  ───────────▶│          │
└─────────┘  255.255.255.255:9200└─────────┘              └──────────┘
     ▲                                                          │
     └────────────  PeerRegistry ◀────────  hello ──────────────┘
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
   docs_remotos = PeerCatalog (entradas públicas de otros nodos, ya en RAM)
   respuesta = docs_locales ⊕ docs_remotos

3. Cliente recibe tabla unificada con columna "Servidor":
   ┌────┬──────────┬──────┬─────────┬────────┬─────────────────┐
   │ ID │ Nombre   │ Tam  │ Hash    │ Origen │ Servidor        │
   ├────┼──────────┼──────┼─────────┼────────┼─────────────────┤
   │ 1  │ a.pdf    │ 2 MB │ 8a3f... │ local  │ Local           │
   │ 5  │ foto.jpg │ 1 MB │ 9b7c... │ remoto │ PC3 (b3c4d5e6)  │
   └────┴──────────┴──────┴─────────┴────────┴─────────────────┘
```

El `PeerCatalog` se refresca solo en background — el listado siempre es instantáneo.

### Flujo de listar clientes / logs / eventos (cross-servidor)

```
Cliente ──LISTAR_CLIENTES──▶ Server A
                               │
                    locales = clienteDAO.listarTodos()      (MySQL A)
                               │
                    para cada peer online:
                    ──PEER_LISTAR_CLIENTES──▶ Server B → respuesta JSON
                    ──PEER_LISTAR_CLIENTES──▶ Server C → respuesta JSON
                               │
                    merge + columna "servidor" en cada fila
                               │
Cliente ◀──── lista unificada ─┘

Mismo patrón para OBTENER_LOGS y OBTENER_EVENTOS.
```

> **Diferencia clave con documentos:** los documentos usan caché en background (`PeerCatalog`); clientes, logs y eventos se consultan en el momento (on-demand). Si un peer está caído, sus filas simplemente no aparecen en esa consulta.

### Flujo de descarga (local vs proxy)

```
Caso 1: archivo LOCAL al servidor que atiende
─────────────────────────────────────────────
Cliente ──DESCARGAR_ARCHIVO {id:1, servidor:"local"}──▶ Server A
                                                          │
                            comprueba acceso (público o privado)
                            MySQL A: SELECT chunks → descifra AES
Cliente ◀──────────── bytes ──────────────────────────────┘


Caso 2: archivo REMOTO (proxy)
──────────────────────────────
Cliente ──DESCARGAR_ARCHIVO {id:5, servidor:"b3c4d5e6..."}──▶ Server A
                                                                 │
                                  busca peerId en PeerRegistry   │
                       Server A ──PEER_DESCARGAR_ARCHIVO──▶ Server B
                                                              │
                                          MySQL B: SELECT chunks → descifra AES
                       Server A ◀────── bytes ──────────────────┘
                                  │
Cliente ◀──────────── bytes ──────┘   (reenvío sin guardar copia)
```

- El cliente nunca habla directamente con B. El servidor A actúa como proxy.
- A **no guarda copia** de lo que pasa por proxy.
- La descarga genera un evento `PEER_DESCARGA_PROXY` en A y en B.

### Envío dirigido y relay

- **Cliente → servidor local:** `ENVIAR_MENSAJE` / `ENVIAR_ARCHIVO` con campos opcionales `envioAlcance`, `destIp`, `destPuerto`, `destProtocolo`, `destServidor`. Por defecto es público (`TODOS`).
- **Cliente → otro servidor:** el servidor origen resuelve el peer y usa `PeerClient` para `PEER_ENTREGAR_*`; el receptor persiste el documento como `DIRIGIDO` con metadatos de origen.
- **Privados en GUI:** bandeja *Docs. privados* (botón refrescar); no hay push TCP de chat entre clientes.

---

## Nombres de clientes

Cuando un cliente se conecta envía su nombre (hostname del SO por defecto, editable en el GUI) con el comando `SET_NOMBRE`. El servidor lo guarda en la tabla `clientes_conectados` y en RAM (`ClientNameCache`). Ese nombre aparece en:

- Pestaña **Clientes** (columna Nombre)
- Pestaña **Documentos** (columna Propietario)
- Pestaña **Eventos** (columna Origen / Cliente)
- Pestaña **Logs** (columna Cliente)

> **Importante:** `ClientNameCache` es en memoria. Si el servidor se reinicia, los nombres se recuperan cuando cada cliente vuelve a conectarse y reenvía `SET_NOMBRE`.

---

## Protocolo — comandos

### Cliente → Servidor

| Comando                    | Descripción                                              |
|----------------------------|----------------------------------------------------------|
| `ENVIAR_ARCHIVO`           | Subir archivo (header JSON + bytes crudos)               |
| `ENVIAR_MENSAJE`           | Enviar mensaje de texto (público o dirigido)             |
| `LISTAR_DOCUMENTOS`        | Docs públicos (locales + remotos via PeerCatalog)        |
| `LISTAR_DOCUMENTOS_PRIVADOS` | Docs dirigidos donde eres destinatario o remitente     |
| `DESCARGAR_ARCHIVO`        | Descarga original (local o proxy si es remoto)           |
| `DESCARGAR_ENCRIPTADO`     | Descarga los bytes cifrados tal como están en BD         |
| `DESCARGAR_HASH`           | Solo el hash SHA-256 del documento                       |
| `LISTAR_CLIENTES`          | Clientes conectados (local + todos los peers)            |
| `LISTAR_SERVIDORES`        | Peers descubiertos en la red                             |
| `OBTENER_EVENTOS`          | Eventos en memoria (local + todos los peers)             |
| `OBTENER_LOGS`             | Logs de BD (local + todos los peers)                     |
| `SET_NOMBRE`               | Registrar/actualizar nombre del cliente                  |

### Servidor → Cliente

| Comando       | Descripción                        |
|---------------|------------------------------------|
| `RESPUESTA`   | OK con datos                       |
| `ERROR`       | Error con detalle                  |
| `SESION_INFO` | Enviado al conectar (bienvenida)   |
| `CHAT_MENSAJE`| Notificación de mensaje recibido   |

### Peer → Peer (servidor a servidor, puerto 9100)

| Comando                  | Descripción                                               |
|--------------------------|-----------------------------------------------------------|
| `PEER_HELLO`             | Handshake inicial con metadatos del nodo                  |
| `PEER_PING`              | Verificar conectividad                                    |
| `PEER_BYE`               | Notificar desconexión                                     |
| `PEER_LISTAR_DOCS`       | Solicitar catálogo de documentos públicos                 |
| `PEER_LISTAR_CLIENTES`   | Solicitar clientes conectados                             |
| `PEER_DESCARGAR_ARCHIVO` | Descargar archivo (responde header + bytes)               |
| `PEER_DESCARGAR_HASH`    | Obtener hash de un documento                              |
| `PEER_ENTREGAR_MENSAJE`  | Relay: entregar mensaje en el servidor destino            |
| `PEER_ENTREGAR_ARCHIVO`  | Relay: entregar archivo en el servidor destino            |
| `PEER_OBTENER_LOGS`      | Solicitar logs de BD del peer                             |
| `PEER_OBTENER_EVENTOS`   | Solicitar eventos en memoria del peer                     |

- **Control:** JSON línea-delimitado `{comando, datos, timestamp}`.
- **Streams:** bytes crudos después del header JSON (TCP) o fragmentados en datagramas `DATOS` + `FIN` (UDP).
- **UDP:** datagramas de 8 KB. Mensajes grandes se fragmentan con `CONTROL_FRAG` + `CONTROL_END` y se reensamblan en el receptor.

---

## Cliente GUI — pestañas

| Pestaña            | Qué muestra                                                                                   | Refresco       |
|--------------------|-----------------------------------------------------------------------------------------------|----------------|
| **Servidores**     | Servidores descubiertos por broadcast: Nombre, Host, TCP, UDP, Peer, ID, Última señal. Botón *"Usar seleccionado"* autocompleta el formulario. | Auto cada 3 s  |
| **Clientes**       | Clientes conectados a **todos** los servidores online. Columnas: Nombre, IP, Puerto, Protocolo, Conectado desde, **Servidor**. | Botón          |
| **Documentos**     | Docs públicos (locales + remotos). Columna *Servidor* indica origen. Descarga proxy transparente. | Botón          |
| **Docs. privados** | Mensajes/archivos dirigidos (destinatario o remitente en este servidor). Columnas: Resumen, Remitente, Destinatario, Origen, Fecha, Tipo, Servidor. | Botón          |
| **Eventos**        | Eventos en memoria de **todos** los servidores online. Columnas: Hora, **Servidor**, Tipo, Origen/Cliente, Puerto, Protocolo, Detalle. | Botón          |
| **Logs (BD)**      | Logs persistidos de **todos** los servidores online. Columnas: Fecha, **Servidor**, Accion, Cliente, Detalle. | Botón          |

**Chat (panel izquierdo):** radios *Enviar a todos* / *Cliente seleccionado*. Los mensajes se anotan con destino legible: `[TÚ → Todos]` o `[TÚ → ip:puerto PROTO @peer=…]`.

---

## Esquema de base de datos

```sql
-- logs: acciones persistidas (conexiones, uploads, descargas)
CREATE TABLE logs (id, accion, ip_origen, fecha_hora, detalles)

-- clientes_conectados: quién está conectado ahora mismo
CREATE TABLE clientes_conectados (ip, puerto, protocolo, fecha_inicio, nombre)

-- documentos: metadatos de cada mensaje o archivo
-- envio_alcance = TODOS | DIRIGIDO
-- dest_* = destinatario si es dirigido
-- remitente_* = snapshot del remitente
-- origen_* = servidor de origen si llegó por relay
CREATE TABLE documentos (id, nombre, extension, tamano, ruta_local_original,
    hash_sha256, ip_propietario, tipo, fecha_creacion,
    envio_alcance, dest_ip, dest_puerto, dest_protocolo,
    origen_servidor_etiqueta, origen_peer_id,
    remitente_nombre, remitente_puerto, remitente_protocolo)

-- documentos_chunks: bytes cifrados en fragmentos de ≤50 MB
-- codificacion = RAW | BASE64
CREATE TABLE documentos_chunks (id, documento_id, chunk_index, datos_encriptados, codificacion)
```

En instalaciones existentes, las columnas nuevas se añaden automáticamente con un procedimiento almacenado compatible con MySQL 8.0+:
```sql
CALL agregar_columna_si_no_existe('tabla', 'columna', 'DEFINICION');
```

---

## Puertos

| Servicio        | Puerto | Protocolo            |
|-----------------|--------|----------------------|
| Cliente TCP     | 9000   | TCP                  |
| Cliente UDP     | 9001   | UDP                  |
| Panel web       | 8080   | HTTP                 |
| Peer-to-peer    | 9100   | TCP entre servidores |
| Discovery       | 9200   | UDP broadcast        |
| MySQL           | 33306  | TCP (Docker)         |

---

## Requisitos

- Java 17+
- Docker + Docker Compose (para MySQL)
- Maven

---

## Levantamiento

```bash
# 1. MySQL
docker compose up -d

# 2. Compilar
mvn -DskipTests package

# 3. Servidor
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" com.app.server.ServerApp

# 4. Cliente
java -cp "client/target/client-1.0-SNAPSHOT.jar:client/target/libs/*" com.app.client.ClientApp
```

### Múltiples servidores (P2P)

**Mismo PC (pruebas):** segundo nodo con puertos distintos:
```bash
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" \
  com.app.server.ServerApp \
  --nombre=PC-SalaB --tcp=9010 --udp=9011 --http=8081 --peer=9110
```

**PCs distintos en la misma LAN:**
1. `docker compose up -d` en cada PC (cada uno con su propia MySQL).
2. Abrir en el firewall: TCP `9000`, `9100`, `8080` y UDP `9001`, `9200`.
3. Levantar:
```bash
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" \
  com.app.server.ServerApp --nombre=PC-Sala-A
```

Si omites `--nombre` se usa el hostname del SO. Si tienes varias interfaces de red y el servidor elige la IP equivocada, fuerza con `--host=192.168.1.50`.

### Migrar una BD existente

Para un arranque limpio (borra todos los datos):
```bash
docker compose down -v && docker compose up -d
```

---

## Flags CLI del servidor

| Flag              | Default  | Descripción                                  |
|-------------------|----------|----------------------------------------------|
| `--tcp=N`         | 9000     | puerto TCP de clientes                       |
| `--udp=N`         | 9001     | puerto UDP de clientes                       |
| `--http=N`        | 8080     | puerto del panel web                         |
| `--peer=N`        | 9100     | puerto TCP entre servidores                  |
| `--discovery=N`   | 9200     | puerto UDP broadcast de descubrimiento       |
| `--max=N`         | 10       | clientes máximos por pool                    |
| `--host=IP`       | auto     | IP a anunciar (si hay varias interfaces)     |
| `--nombre=texto`  | hostname | alias legible del servidor                   |
| `--peers=off`     | on       | desactiva el módulo P2P                      |

## Comandos del servidor (consola interactiva)

| Comando         | Descripción                                |
|-----------------|--------------------------------------------|
| `status`        | clientes activos en pool TCP/UDP           |
| `peers`         | peers en línea con nombre, host, puertos   |
| `remotos`       | documentos publicados por peers            |
| `events [N]`    | últimos N eventos del buffer (default 20)  |
| `events on/off` | activa/desactiva log de eventos en consola |
| `logs [N]`      | últimos N logs de BD (default 20)          |
| `exit`          | apaga el servidor                          |

---

## Logs y eventos — cuatro lugares

1. **Archivo** — `storage/server-logs/server-YYYYMMDD-HHmmss.log` (uno por arranque).
2. **Consola del servidor** — formato `[HH:mm:ss] [CAT] TIPO | detalles`. Categorías: `SYS`, `NET`, `POOL`, `STOR`, `MSG`, `PEER`, `ERR`.
3. **Panel web** — pestañas *Eventos* (buffer en memoria, refresh 2 s) y *Logs (BD)*.
4. **Cliente GUI** — pestañas *Eventos* y *Logs (BD)* muestran datos de **todos los servidores online**, con columna **Servidor** indicando el origen de cada fila.

> Los **Eventos** son el buffer en RAM de los últimos 500 eventos técnicos (conexiones TCP, peers descubiertos, errores, etc.). Los **Logs** son las acciones de negocio persistidas en MySQL (conexión de cliente, archivo recibido, descarga). Son complementarios, no redundantes.

---

## API HTTP

| Método | Endpoint                                             | Notas                                    |
|--------|------------------------------------------------------|------------------------------------------|
| GET    | `/api/health`                                        | estado del gateway                       |
| POST   | `/api/connect?port=8080`                             | abre sesión, devuelve `sessionId`        |
| POST   | `/api/disconnect`                                    | header `X-Session-Id`                    |
| GET    | `/api/documentos`                                    | lista (locales + remotos)                |
| GET    | `/api/clientes`                                      | clientes conectados                      |
| GET    | `/api/peers`                                         | peers en línea con `nombre`              |
| GET    | `/api/events?limit=100`                              | eventos recientes (memoria, solo locales)|
| GET    | `/api/logs?limit=50`                                 | últimos logs de BD (solo locales)        |
| POST   | `/api/upload?filename=x.pdf`                         | subir archivo (`X-Session-Id`, body bin) |
| GET    | `/api/download?documentoId=1&tipo=ORIGINAL`          | `tipo`: ORIGINAL, HASH, ENCRIPTADO       |
| POST   | `/api/chat`                                          | `{texto}` (`X-Session-Id`)               |
| GET    | `/api/chat`                                          | historial                                |

> La API HTTP expone solo datos locales del servidor. La agregación cross-peer está disponible vía el protocolo TCP/UDP del cliente.

---

## Seguridad

- **AES-256/CBC + PBKDF2** para cifrar archivos en BD.
- **SHA-256** para integridad.
- Tamaño máximo de archivo: **1 GB** (validado en stream).
- `sanitizeFileName` rechaza paths con `..` o relativos.
- El canal peer entre servidores es TCP en claro en LAN (TLS queda como mejora futura).
- La descarga encriptada de peers remotos no está soportada (requeriría compartir la clave AES entre nodos).

---

## Tests

```bash
mvn test
```

Cobertura por módulo:
- `shared` — `ComandoTest`, `MensajeTest`, `CryptoUtilTest`.
- `server` — `CommandDispatcherTest`, `UdpFragmentationTest`, `PeerRegistryTest`, `PeerInfoTest`, `PeerPingIntegrationTest`, `PeerRelayIntegrationTest`, `InMemoryEventBufferTest`, `ClientPoolTest`, `ServerModelsTest`.
- `client` — `NetworkClientTcpTest`, `NetworkClientUdpTest`, `ClientDiscoveryServiceTest`, `HistorialDocumentoTest`.

Los tests de integración con MySQL/HTTP real están `@Disabled` y se ejecutan manualmente.

---

## Limitaciones conocidas

- Si un servidor se cae, sus documentos dejan de ser descargables hasta que vuelva (no hay replicación de bytes).
- El catálogo de documentos es **eventualmente consistente**: tras subir a A, B lo verá en ≤10 s.
- `ClientNameCache` es efímera: los nombres se pierden al reiniciar el servidor y se repueblan cuando los clientes vuelven a conectarse.
- Los documentos `DIRIGIDO` no entran en el catálogo público; viven solo en la MySQL del servidor que los almacenó.
- Para multi-PC: el broadcast UDP debe pasar el firewall de cada PC y estar en la misma subred.
