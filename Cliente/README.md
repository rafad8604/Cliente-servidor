# Cliente de Escritorio TCP/UDP - Gestor de Documentos

Aplicación de escritorio Java Swing que se conecta a un servidor TCP/UDP para gestionar documentos, mensajes y listar logs.

## Características

- ✓ Conexión a servidor indicando IP, puerto y protocolo (TCP/UDP)
- ✓ Listar clientes conectados al servidor
- ✓ Listar documentos disponibles (nombre, tamaño, extensión)
- ✓ Enviar mensajes de texto
- ✓ Enviar archivos (uno a la vez o múltiples en paralelo)
- ✓ Recibir documentos del servidor
- ✓ Ver logs del servidor
- ✓ Persistencia en H2 de metadatos de transferencias (enviados)

## Requisitos

- Java 21+
- Maven 3.9+
- Spring Boot 4.0.5+
- H2 Database (incluido en dependencias)

## Compilación

```bash
cd Cliente
mvn clean compile
```

## Ejecución

### Opción 1: Ejecutar con Maven

```bash
mvn spring-boot:run
```

### Opción 2: Crear JAR ejecutable

```bash
mvn clean package
java -jar target/Cliente-0.0.1-SNAPSHOT.jar
```

## Configuración

Edita `src/main/resources/application.properties` para personalizar:

```properties
# Timeouts (milisegundos)
client.network.tcp-timeout-ms=5000
client.network.udp-timeout-ms=5000

# Máximo de descargas paralelas
client.transfer.max-parallel-uploads=4

# Base de datos H2
spring.datasource.url=jdbc:h2:file:./data/clientedb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
```

## Uso

### 1. Conectar al servidor

1. Inicia la aplicación
2. En el panel "Conexión al Servidor", ingresa:
   - **IP/Host**: Dirección del servidor (ej: `localhost`, `192.168.1.100`)
   - **Puerto**: Puerto de escucha (ej: `5000`)
   - **Protocolo**: TCP o UDP
3. Click en **Conectar**

### 2. Listar clientes conectados

1. Ve a la pestaña "Clientes Conectados"
2. Click en **Actualizar**
3. Se mostrará el total de clientes y detalles de conexión

### 3. Listar documentos disponibles

1. Ve a la pestaña "Documentos Disponibles"
2. Click en **Actualizar**
3. Se mostrarán: nombre, tamaño (bytes), extensión y propietario

### 4. Enviar mensaje

1. Ve a la pestaña "Enviar Mensaje"
2. Ingresa el ID del destinatario
3. Escribe tu mensaje en el área de texto
4. Click en **Enviar Mensaje**
5. Confirma el resultado (se guarda el registro en H2)

### 5. Enviar archivo

1. Ve a la pestaña "Enviar Archivo"
2. Selecciona uno o varios archivos
3. Los archivos se enviarán en paralelo (hasta 4 simultáneos)

### 6. Ver logs del servidor

1. Ve a la pestaña "Logs del Servidor"
2. Click en **Actualizar**
3. Se mostrarán los eventos del servidor

## Arquitectura

```
com.arquitectura.cliente/
├── domain/                      # Entidades del dominio
│   ├── TransportProtocol.java  # Enum TCP/UDP
│   └── ConnectionSettings.java  # Record de configuración
├── application/                 # Lógica de negocios
│   ├── ConnectionManager.java   # Gestor de conexión
│   ├── QueryService.java        # Consultas (clientes, docs, logs)
│   └── TransferService.java     # Transferencias (envío/recepción)
├── infrastructure/
│   └── protocol/               # Adaptadores de protocolo
│       ├── TransportClient.java          # Interfaz
│       ├── TcpTransportClient.java       # Implementación TCP
│       └── UdpTransportClient.java       # Implementación UDP
├── persistence/                # Persistencia H2
│   ├── SentTransferEntity.java          # Entidad JPA
│   └── SentTransferRepository.java      # Repository
└── presentation/swing/         # UI Swing
    └── DesktopMainFrame.java   # Ventana principal
```

## Protocolo de Comunicación

### Request JSON (TCP/UDP)

```json
{
  "service": "CONNECTED_CLIENTS_LIST",
  "senderId": "client1",
  "payloadLength": 0
}
```

### Services soportados

- `CONNECTED_CLIENTS_COUNT`: Obtener cantidad de clientes
- `CONNECTED_CLIENTS_LIST`: Listar clientes activos
- `DOCUMENTS_LIST`: Listar documentos disponibles
- `LOG_REPORT`: Obtener logs del servidor
- `RECEIVE_DOCUMENT`: Recibir documento (TCP: con binario)
- `SEND_DOCUMENT`: Descargar documento

### TCP vs UDP

| Operación | TCP | UDP |
|-----------|-----|-----|
| Consultas (clientes, docs, logs) | ✓ | ✓ |
| Mensajes de texto | ✓ | ✓ |
| Archivos binarios | ✓ | ✗ |
| Garantía de entrega | ✓ | ✗ |

**Nota**: UDP rechazará operaciones con archivos binarios automáticamente.

## Persistencia H2

La aplicación guarda en `data/clientedb.h2.db` todos los envíos realizados con:

- Timestamp de envío
- Servidor destino (IP:puerto)
- Protocolo utilizado (TCP/UDP)
- Tipo: MESSAGE o FILE
- Contenido (preview para mensajes, nombre para archivos)
- Tamaño (bytes)
- Estado (SENT, PENDING, FAILED)
- ID del documento en servidor
- Errores si los hubo

Consulta con H2 Console (si la activas):

```sql
SELECT * FROM sent_transfers ORDER BY sent_at DESC;
SELECT COUNT(*) FROM sent_transfers WHERE protocol='TCP';
SELECT COUNT(*) FROM sent_transfers WHERE status='FAILED';
```

## Concurrencia

- Máximo de **4 descargas paralelas** por defecto (configurable)
- Cada archivo se rastreatiene su propio estado
- Los timeouts son independientes por protocolo
- Thread pool gestionado por Spring

## Manejo de errores

- Conexión rechazada: Muestra diálogo con el error
- Timeout: Se reintenta automáticamente
- UDP rechaza binarios: Fallback sugerido a TCP
- Errores en transferencias: Se registran en H2

## Próximas mejoras

- [ ] Selector visual de archivos (JFileChooser)
- [ ] Progreso de transferencia por archivo
- [ ] Descarga de archivos con guardado a disco
- [ ] Estadísticas de transferencias
- [ ] Panel de configuración de timeouts en UI
- [ ] Validación de integridad (hash SHA256)
- [ ] Reconexión automática

## Desarrollo

### Ejecutar tests

```bash
mvn test
```

### Limpiar base de datos

```bash
rm -rf data/clientedb.h2.db
```

### Debug

Establece breakpoints en la clase `DesktopMainFrame` o en los servicios.

## Soporte

Para reportar problemas:

1. Verifica que el servidor esté corriendo
2. Comprueba la conectividad: `telnet localhost 5000`
3. Revisa los logs en la consola de la aplicación
4. Consulta `data/clientedb.h2.db` para ver el historial de transferencias

