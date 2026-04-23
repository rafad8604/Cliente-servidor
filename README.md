# Sistema Cliente-Servidor de Mensajería y Archivos

Proyecto Java multi-módulo para mensajería y transferencia de archivos con soporte de protocolos **TCP**, **UDP** e **HTTP**.

## Características

- Envío de mensajes y archivos vía **TCP**, **UDP** o **HTTP**
- Cliente con GUI (Swing + FlatLaf) - soporta TCP y UDP
- Panel web HTTP para acceso desde navegador
- Servidor con almacenamiento de metadatos y logs en MySQL
- Cifrado AES-256 y hash SHA-256 para todos los archivos
- Historial local del cliente con H2
- Log por sesión de ejecución del servidor (archivo nuevo por cada arranque)
- 65+ test cases para validación de funcionalidad

## Arquitectura del proyecto

- `shared`: contrato/protocolo y utilidades compartidas entre cliente y servidor.
  - `Comando`, `Mensaje`
  - `CryptoUtil` - AES-256 y SHA-256
- `server`: núcleo del servidor, handlers TCP/UDP, gateway HTTP, servicios y DAOs MySQL.
- `client`: aplicación de escritorio con GUI y cliente de red (TCP/UDP).
- `storage`: almacenamiento local usado por el proyecto (logs, archivos temporales).

## Protocolos soportados

### TCP (Puerto 9000)
- Conexión persistente
- Cliente: Selecciona en GUI → "TCP"
- Registra cliente automáticamente al conectar
- Ideal para: sesiones largas, transferencias confiables

### UDP (Puerto 9001)
- Datagrama sin conexión
- Cliente: Selecciona en GUI → "UDP"
- Registra cliente automáticamente al enviar primer comando
- Nota: cliente calcula puerto como `9000 + 1 = 9001`
- Ideal para: baja latencia, tolerancia a pérdida de paquetes

### HTTP (Puerto 8080)
- Con sesión HTTP explícita (`/api/connect` y `/api/disconnect`)
- Acceso: navegador web → `http://localhost:8080`
- Registra cliente al conectarse y lo elimina al desconectarse
- Ideal para: acceso web, monitoreo y operaciones desde navegador con estado de sesión

## Requisitos

- Java 17+
- Docker y Docker Compose (para MySQL)
- Maven (opcional si ya tienes los `target` versionados)

## Base de datos (MySQL)

El proyecto incluye `docker-compose.yml` para levantar MySQL con esquema inicial automático:

```bash
docker compose up -d
```

Credenciales por defecto:

- Base de datos: `mensajeria_db`
- Usuario: `mensajeria_user`
- Password: `mensajeria_pass`
- Puerto: `3306`

## Compilar y empaquetar (con Maven)

Desde la raíz del proyecto:

```bash
mvn -q clean package
```

Esto genera los artefactos en:

- `shared/target/`
- `server/target/`
- `client/target/`

## Ejecutar el servidor

Desde la raíz:

```bash
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" com.app.server.ServerApp
```

### Puertos usados por defecto:

| Protocolo | Puerto | Descripción                    |
|-----------|--------|--------------------------------|
| TCP       | 9000   | Conexión persistente (cliente) |
| UDP       | 9001   | Datagrama (cliente)            |
| HTTP      | 8080   | Panel web (navegador)          |

### Panel Web HTTP:

- URL: `http://localhost:8080`
- Conexión/desconexión HTTP desde el propio panel
- Funcionalidades:
  - Chat HTTP (envío y visualización de historial)
  - Subir archivos al servidor
  - Ver documentos guardados en BD
  - Descargar documentos (`Original`, `Hash`, `Encriptado`)
  - Ver clientes conectados (TCP, UDP, HTTP)
  - Ver logs recientes del servidor
  - Interfaz responsive dark theme

### Endpoints HTTP principales

- `GET /api/health` - estado del gateway
- `POST /api/connect?port=8080` - abre sesión HTTP y registra cliente
- `POST /api/disconnect` - cierra sesión HTTP (`X-Session-Id`)
- `GET /api/documentos` - lista documentos
- `GET /api/clientes` - lista clientes conectados
- `GET /api/logs?limit=50` - últimos logs
- `POST /api/chat` - envía mensaje (`X-Session-Id`, body JSON `{ "texto": "..." }`)
- `GET /api/chat` - historial de chat HTTP
- `POST /api/upload?filename=archivo.ext` - sube archivo (`X-Session-Id`)
- `GET /api/download?documentoId=1&tipo=ORIGINAL|HASH|ENCRIPTADO` - descarga por tipo (`X-Session-Id`)

### Comandos de consola del servidor:

- `status` → muestra clientes activos en BD
- `exit` → detiene el servidor (cierra todos los puertos)

## Ejecutar el cliente

Desde la raíz:

```bash
java -cp "client/target/client-1.0-SNAPSHOT.jar:client/target/libs/*" com.app.client.ClientApp
```

Puedes abrir múltiples instancias del cliente para pruebas de mensajería/transferencia.

## Ejecutar pruebas

Desde la raíz:

```bash
mvn -q test
```

### Cobertura de tests (65+ casos):

**shared/**
- `ComandoTest` - Validación de 11 comandos del protocolo
- `MensajeTest` - Serialización/deserialización JSON
- `CryptoUtilTest` - SHA-256 y AES-256

**server/**
- `HttpGatewayTest` (11 tests) - Endpoints REST y panel web
- `DocumentoServiceTest` (6 tests) - Procesamiento de archivos, hash, encriptación
- `LogServiceTest` (11 tests) - Persistencia de logs con múltiples IPs/acciones
- `CommandDispatcherTest` (10 tests) - Estructura de comandos
- `ClienteConectadoDAOTest` (10 tests) - Persistencia de conexiones (TCP, UDP, HTTP)
- `ClientPoolTest` - Pool de conexiones TCP
- `ServerModelsTest` - Entidades (Documento, ClienteConectado, Log)

**client/**
- `NetworkClientTcpTest` - Envío y descarga de archivos vía TCP
- `NetworkClientUdpTest` (10 tests) - Conexión UDP, puerto correcto (9001), handshake
- `HistorialDocumentoTest` - Historial local H2

## Flujo sin Maven (usando `target` versionado)

Si trabajas en una máquina sin Maven, puedes ejecutar directamente si ya están versionados los artefactos en `target`:

1. Levantar MySQL con Docker:

```bash
docker compose up -d
```

2. Iniciar servidor:

```bash
java -cp "server/target/server-1.0-SNAPSHOT.jar:server/target/libs/*" com.app.server.ServerApp
```

3. Iniciar cliente:

```bash
java -cp "client/target/client-1.0-SNAPSHOT.jar:client/target/libs/*" com.app.client.ClientApp
```

## Logs de ejecución del servidor

En cada arranque del servidor se crea un log nuevo en:

- `storage/server-logs/`

Formato de archivo:

- `server-YYYYMMDD-HHmmss.log`

## Notas útiles

- Si MySQL no está disponible al arrancar el servidor, el inicio fallará.
- Asegúrate de mantener libres los puertos: `9000` (TCP), `9001` (UDP), `3306` (MySQL), `8080` (HTTP).
- El proyecto está organizado como multi-módulo Maven (`shared`, `server`, `client`).
- **UDP**: El cliente calcula automáticamente el puerto como `puerto_tcp + 1` (9000 → 9001).
- **HTTP**: Requiere sesión para chat, subida y descarga (header `X-Session-Id`).
- **Cifrado**: Todos los archivos se cifran con AES-256 y se almacenan como chunks en BD.
- **Testing**: Ejecuta `mvn test` para validar funcionalidad en los 3 protocolos.
