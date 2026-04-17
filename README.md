# Sistema Cliente-Servidor de Mensajería y Archivos

Proyecto Java multi-módulo para mensajería y transferencia de archivos con soporte de protocolos TCP y UDP.

## Características

- Envío de mensajes y archivos.
- Soporte de comunicación TCP y UDP.
- Cliente con GUI (Swing + FlatLaf).
- Servidor con almacenamiento de metadatos y logs en MySQL.
- Cifrado y utilidades compartidas en módulo común.
- Historial local del cliente con H2.
- Log por sesión de ejecución del servidor (archivo nuevo por cada arranque).

## Arquitectura del proyecto

- `shared`: contrato/protocolo y utilidades compartidas entre cliente y servidor.
  - `Comando`, `Mensaje`
  - `CryptoUtil`
- `server`: núcleo del servidor, handlers TCP/UDP, servicios y DAOs MySQL.
- `client`: aplicación de escritorio con GUI y cliente de red.
- `storage`: almacenamiento local usado por el proyecto (por ejemplo, logs de sesión).

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

Puertos usados por defecto:

- TCP: `9000`
- UDP: `9001`

Comandos de consola del servidor:

- `status` → muestra clientes activos
- `exit` → detiene el servidor

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
- Asegúrate de mantener libre los puertos `9000`, `9001` y `3306`.
- El proyecto está organizado como multi-módulo Maven (`shared`, `server`, `client`).
