# Sistema P2P de Mensajería y Archivos

Proyecto Java multi-módulo. Varios servidores se descubren entre sí por UDP broadcast en la LAN, comparten su catálogo de documentos y un cliente conectado a cualquier servidor puede listar y descargar archivos de los demás.

## Arquitectura

```
┌──────────┐         ┌─────────────┐                      ┌─────────────┐
│ Cliente  │  TCP/   │  Servidor A │   TCP peer-to-peer   │  Servidor B │
│ (Swing)  │◀──UDP──▶│             │◀────────────────────▶│             │
│          │  9000/  │  TCP 9000   │   puerto 9100        │             │
│  o HTTP  │  9001/  │  UDP 9001   │   discovery UDP 9200 │             │
│          │  8080   │  HTTP 8080  │   (broadcast)        │             │
└──────────┘         └──────┬──────┘                      └─────────────┘
                            │
                       MySQL :33306
```

- **`shared/`** — `Comando`, `Mensaje` (Gson JSON línea-delimitado), `CryptoUtil` (AES-256, SHA-256, PBKDF2).
- **`server/`**
  - `net/` — TCP (`ServerCore`, `ClientHandler`, `TcpClientChannel`) y UDP (`UdpHandler`, `UdpClientChannel`) con fragmentación automática de mensajes grandes.
  - `peer/` — `PeerDiscoveryService` (broadcast UDP), `PeerRegistry` (TTL), `PeerServer/PeerSession` (TCP entre servidores), `PeerClient`, `PeerCatalog` (caché con TTL de docs remotos), `PeerProxyService` (proxy de descarga).
  - `service/` — `DocumentoService` (cifrado, hash, chunks 50 MB), `LogService`.
  - `dao/` — JDBC con pool de 10 conexiones.
  - `http/` — `HttpGateway` (REST + UI estática).
  - `events/` — bus de eventos asíncrono + consola formateada.
- **`client/`** — `NetworkClient` (TCP/UDP, reensamblaje de fragmentos), GUI Swing + FlatLaf, H2 local para historial.

### Comunicación entre servidores

1. **Descubrimiento** — cada servidor envía `PEER_HELLO` por UDP broadcast a `255.255.255.255:9200` cada 5 s. Si un peer no avisa en 15 s se marca offline.
2. **Catálogo** — cada 10 s cada servidor pide `PEER_LISTAR_DOCS` por TCP (puerto `9100`) a los peers online y cachea el resultado (TTL 30 s).
3. **Descarga proxy** — cuando un cliente pide un documento con `servidor=<peerId>`, el servidor local abre TCP al peer, descarga y reenvía al cliente. Transparente para el cliente.

### Protocolo

- Control: JSON línea-delimitado con `{comando, datos, timestamp}`.
- Streams binarios: bytes crudos después del header JSON (TCP) o fragmentados en datagramas tipo `DATOS` + `FIN` (UDP).
- UDP usa datagramas de **8 KB** (compatible con `net.inet.udp.maxdgram` por defecto en macOS). Mensajes JSON más grandes se fragmentan en `CONTROL_FRAG` + `CONTROL_END` y se reensamblan en el receptor.

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

En la misma máquina, levantar un segundo nodo con puertos distintos:

```bash
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" \
  com.app.server.ServerApp \
  --tcp=9010 --udp=9011 --http=8081 --peer=9110
```

Los nodos se descubren solos por broadcast. Flag `--peers=off` desactiva P2P.

## Comandos del servidor (consola)

| Comando      | Descripción                                  |
|--------------|----------------------------------------------|
| `status`     | clientes activos en pool TCP/UDP             |
| `peers`      | peers en línea                               |
| `remotos`    | documentos publicados por peers              |
| `events on/off` | activa/desactiva log de eventos           |
| `exit`       | apaga el servidor                            |

## API HTTP

| Método | Endpoint                       | Notas                                    |
|--------|--------------------------------|------------------------------------------|
| GET    | `/api/health`                  | estado del gateway                       |
| POST   | `/api/connect?port=8080`       | abre sesión, devuelve `sessionId`        |
| POST   | `/api/disconnect`              | header `X-Session-Id`                    |
| GET    | `/api/documentos`              | lista (locales + remotos)                |
| GET    | `/api/clientes`                | clientes conectados                      |
| GET    | `/api/peers`                   | peers en línea                           |
| GET    | `/api/logs?limit=50`           | últimos logs                             |
| POST   | `/api/upload?filename=x.pdf`   | subir archivo (`X-Session-Id`, body bin) |
| GET    | `/api/download?documentoId=1&tipo=ORIGINAL` | `tipo`: ORIGINAL, HASH, ENCRIPTADO |
| POST   | `/api/chat`                    | `{texto}` (`X-Session-Id`)               |
| GET    | `/api/chat`                    | historial                                |

## Seguridad

- **AES-256/CBC + PBKDF2** para cifrar archivos en BD.
- **SHA-256** para integridad.
- Tamaño máximo de archivo: **1 GB** (validado en stream).
- `sanitizeFileName` rechaza paths con `..` o relativos.

## Tests

```bash
mvn test
```

86 tests pasan en CI (40 tests de integración con MySQL/HTTP real están `@Disabled` y se corren manualmente).

Cobertura por módulo:
- `shared` — `ComandoTest`, `MensajeTest`, `CryptoUtilTest`.
- `server` — `CommandDispatcherTest`, `UdpFragmentationTest`, `PeerRegistryTest`, `PeerInfoTest`, `PeerPingIntegrationTest`, `ClientPoolTest`, `ServerModelsTest`.
- `client` — `NetworkClientTcpTest`, `NetworkClientUdpTest`, `HistorialDocumentoTest`.

## Logs

Cada arranque crea `storage/server-logs/server-YYYYMMDD-HHmmss.log`. Los eventos van también a consola con formato `[HH:mm:ss] [CAT] TIPO | detalles`, donde `CAT` es `SYS`, `NET`, `POOL`, `STOR`, `MSG`, `PEER` o `ERR`.

## Notas

- MySQL debe estar arriba antes del servidor.
- Liberar puertos: `9000`, `9001`, `8080`, `9100`, `9200`, `33306`.
- El cliente GUI muestra documentos locales y remotos en la misma tabla (columna `servidor`).
- Descarga encriptada de peers remotos no soportada (requeriría compartir clave AES entre nodos).
