# Arquitectura - Protocolo de Comunicacion TCP/UDP

## Resumen de Cambios

Se agrego la funcionalidad de **protocolo de comunicacion configurable** (TCP/UDP), permitiendo cambiar el protocolo y puerto sin recompilar codigo.

Se aplica el patron **Adapter** para desacoplar la seleccion de protocolo de la logica del servicio.

## Componentes Nuevos

### 1. `CommunicationProtocol` (enum)
**Ubicacion**: `com.arquitectura.servidor.business.infrastructure`

Enumeracion que define protocolos soportados:
- `TCP`: "Transmission Control Protocol - conexion confiable"
- `UDP`: "User Datagram Protocol - conexion sin garantia"

Metodos:
- `getName()`: nombre del protocolo (TCP/UDP).
- `getDescription()`: descripcion detallada.
- `fromString(String)`: parsea string a enum (case-insensitive, requiere valor valido TCP/UDP).

**Por que**: abstrae tipos de protocolo y facilita extension futura.

### 2. `ProtocolConfig` (componente Spring)
**Ubicacion**: `com.arquitectura.servidor.business.infrastructure`

Lee configuracion desde `protocol.properties`:
- Propiedades: `server.protocol` y `server.port`
- Validacion: puerto entre 1 y 65535.
- Metodos: `getProtocol()`, `getPort()`, `getProtocolName()`, `getProtocolDescription()`.

**Por que**: centraliza lectura de propiedades y valida rangos.

### 3. `ProtocolAdapter` + adapters concretos
**Ubicacion**: `com.arquitectura.servidor.business.infrastructure`

Define la interfaz del Adapter:
- `start(port)`
- `stop()`
- `isListening()`
- `description()`

Adapters concretos:
- `TcpProtocolAdapter`
- `UdpProtocolAdapter`

### 4. `CommunicationService` (servicio)
**Ubicacion**: `com.arquitectura.servidor.business.infrastructure`

Gestiona estado de comunicacion:
- `startListening()`: inicia escucha en protocolo/puerto configurado.
- `stopListening()`: detiene escucha.
- `getServerInfo()`: retorna "PROTOCOLO:PUERTO (descripcion)".
- `isListening()`: estado actual.

Tambien resuelve el adapter correcto directamente con la lista inyectada por Spring (`List<ProtocolAdapter>`), evitando una capa Factory adicional.

Integración Observer: emite eventos `PROTOCOLO` con detalles de inicio/cierre.

**Por que**: abstrae detalles de comunicacion y notifica cambios via Observer.

### 5. `ProtocolListenerRunner` (CommandLineRunner)
**Ubicacion**: `com.arquitectura.servidor.presentation.console`

Inicia/detiene el servicio de comunicacion:
- Al arrancar: llama `startListening()`.
- Al apagar: llama `stopListening()`.

**Por que**: integra ciclo de vida del protocolo con Spring Boot.

## Flujo de Funcionamiento

1. **Lectura de configuracion**: `ProtocolConfig` lee `protocol.properties`.
2. **Arranque**: `ProtocolListenerRunner` inicia `CommunicationService`.
3. **Escucha activa**: servidor escucha en protocolo/puerto definido.
4. **Observacion**: evento `PROTOCOLO` es emitido via `ServerActivitySource`.
5. **Visualizacion**: `ConsoleActivityObserver` imprime detalles en consola.
6. **Apagado**: al cerrar, se emite evento `PROTOCOLO` de cierre.

## Archivos de Configuracion

### `protocol.properties` (nuevo)
```properties
server.protocol=TCP
server.port=5000
```

**Opciones de protocolo**:
- `TCP`: conexion confiable (ordenada, con retransmision).
- `UDP`: sin garantia (rapido, sin retransmision).

**Puerto**: cualquier numero entre 1-65535. Recomendado usar puertos > 1024 (no requieren permisos administrativos).

## Pruebas Nuevas

- `CommunicationProtocolTest`: 6 pruebas de enum.
- `ProtocolConfigTest`: 5 pruebas de configuracion y validacion de puerto.
- `CommunicationServiceTest`: 7 pruebas de servicio (inicio/parada, info, cambios, adapter faltante).

**Total**: 18 nuevas pruebas. **Total general**: 28 tests.

## Cambiar Configuracion

### Cambiar a UDP
Edita `src/main/resources/protocol.properties`:
```properties
server.protocol=UDP
server.port=5000
```

### Cambiar puerto
Edita `src/main/resources/protocol.properties`:
```properties
server.protocol=TCP
server.port=8080
```

Ejecuta el servidor y veras en consola:
```
[2026-04-14 12:00:00] [Servidor] [PROTOCOLO] Servidor escuchando en puerto 8080 usando TCP (Transmission Control Protocol - conexion confiable)
```

## Proximos Pasos

Cuando se implemente **cliente real**:
1. Cliente conectara usando TCP o UDP segun configuracion.
2. `CommunicationService` podra extenderse para crear sockets reales.
3. Eventos de conexion/desconexion de clientes se emitiran via Observer.

