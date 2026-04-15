# Servidor (Consola) - Observer

Este modulo implementa un servidor de consola usando el patron **Observer** de forma simple:

## Patrones de Diseno

### Observer Pattern
- `Observable`: contrato para registrar, remover y notificar observadores.
- `Observer`: contrato para recibir cambios.
- `ServerActivitySource`: observado concreto que emite eventos de actividad.
- `ConsoleActivityObserver`: observador concreto que imprime en consola.

## Funcionalidades

### Gestion de Conexiones de Usuarios
- **Limite de usuarios**: máximo 5 conectados simultáneamente.
- **Configurable**: editar `user.properties` para cambiar el limite.
- **Eventos**: CONEXION, DESCONEXION, CONEXION_RECHAZADA.
- **Object Pool**: reutiliza workers de conexion para controlar recursos.
- **Hilo por conexion**: cada usuario conectado se procesa en su propio thread.
- **Servicio a clientes**: cantidad de clientes conectados.
- **Servicio a clientes**: listado de clientes conectados (IP, fecha y hora de inicio).
- **Formato de intercambio**: JSON para las respuestas entre cliente y servidor.

Ejemplos JSON:
```json
{"service":"CONNECTED_CLIENTS_COUNT","connectedClients":2}
```

```json
{"service":"CONNECTED_CLIENTS_LIST","clients":[{"ipAddress":"10.1.1.1","startDate":"2026-04-14","startTime":"13:31:43"}]}
```

### Protocolo de Comunicacion (TCP/UDP)
- **Soporta**: TCP (conexion confiable) y UDP (sin garantia).
- **Configurable**: editar `protocol.properties` para cambiar protocolo.
- **Puerto configurable**: modificar puerto en `protocol.properties`.
- **Eventos**: PROTOCOLO (inicio/cierre de escucha).

### Recepcion de documentos (mensajes y archivos)
- **Entrada JSON**: metadatos de transferencia (`senderId`, `recipientId`, `senderIp`, `type`, `fileName`/`message`).
- **Directorio de originales**: cada documento se guarda en `originalFiles`.
- **Procesamiento seguro**: hash SHA-256 del contenido original + cifrado AES/GCM.
- **Persistencia MySQL**: se guarda hash, IV, algoritmo, tamanos y payload cifrado en `received_documents`.
- **Archivos grandes**: procesamiento por streaming con buffer configurable para soportar >1 GB.

### Servicios JSON expuestos a clientes
- `CONNECTED_CLIENTS_COUNT`: cantidad de clientes conectados.
- `CONNECTED_CLIENTS_LIST`: listado de clientes conectados (IP, fecha, hora inicio).
- `DOCUMENTS_LIST`: listado de documentos (nombre, tamano, extension, propietario local/externo, IP origen).
- `LOG_REPORT`: informe de logs de mensajes/archivos enviados/recibidos.
- `RECEIVE_DOCUMENT`: recibir mensaje o archivo desde cliente.
- `SEND_DOCUMENT`: enviar documento al cliente en modo `ORIGINAL`, `ORIGINAL_WITH_HASH` o `ENCRYPTED`.
- Rechazo de conexion TCP cuando no hay cupo: `CONNECTION_REJECTED`.

Ejemplo de metadata JSON para mensaje:
```json
{"senderId":"Alice","recipientId":"Bob","type":"MESSAGE","message":"Hola"}
```

Ejemplo de metadata JSON para archivo:
```json
{"senderId":"Alice","recipientId":"Bob","senderIp":"10.0.0.15","type":"FILE","fileName":"reporte.pdf","payloadLength":1048576}
```

Ejemplo enviar documento con hash:
```json
{"service":"SEND_DOCUMENT","documentId":"<id>","mode":"ORIGINAL_WITH_HASH"}
```

Ejemplo enviar documento cifrado:
```json
{"service":"SEND_DOCUMENT","documentId":"<id>","mode":"ENCRYPTED"}
```

## Configuracion

### user.properties
```properties
server.max.users=5
```

### protocol.properties
```properties
server.protocol=TCP
server.port=5000
```

### document.properties
```properties
server.documents.original-dir=originalFiles
server.documents.encryption-secret=cambia-esta-clave-en-produccion
server.documents.buffer-size-bytes=1048576
```

### Base de datos MySQL
- Script de creacion: `database/mysql/create_database.sql`
- Migracion de tabla: `src/main/resources/db/migration/V1__create_received_documents.sql`

Variables (opcionales) para ejecutar:
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/servidor_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="root"
```

Cambia `server.protocol` a `UDP` si necesitas protocolo sin garantia.

## Ejecucion

```powershell
.\mvnw.cmd spring-boot:run
```

Veras en consola:
- `INICIO`: servidor arrancado.
- `PROTOCOLO`: servidor escuchando (TCP/UDP en puerto).
- `HEARTBEAT`: pulsos periodicos.
- `CONEXION`: nuevo usuario conectado.
- `CONEXION_HILO`: hilo que procesa la conexion.
- `CONEXION_RECHAZADA`: limite alcanzado.
- `DESCONEXION`: usuario desconectado.
- `CIERRE`: servidor apagando.

## Pruebas

```powershell
.\mvnw.cmd test
```

Se ejecutan 46 pruebas unitarias/integracion:
- Observer pattern (notificacion, suscripcion, desuscripcion).
- Servicio de conexiones (limite, desconexion, excepciones).
- Object Pool de conexiones y ciclo de vida de workers.
- Protocolo de comunicacion y ruteo de servicios JSON (TCP, UDP, puerto, comandos).
- Recepcion y consulta de documentos (hash/cifrado, respuestas JSON, repositorios de prueba).
- Salida en consola.

