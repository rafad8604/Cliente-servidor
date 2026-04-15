# Arquitectura - Funcionalidad de Conexiones

## Resumen de Cambios

Se agregó la funcionalidad de **gestión de conexiones de usuarios** con límite configurable, manteniendo el patrón **Observer** como base.

Tambien se agrega el patron **Object Pool** para reutilizar workers de conexion y procesar cada conexion en su propio hilo de ejecucion.

## Componentes Nuevos

### 1. `UserConnection` (record)
**Ubicación**: `com.arquitectura.servidor.business.user`

Representa una conexión activa de usuario:
- `userId`: identificador único (UUID).
- `username`: nombre de usuario.
- `ipAddress`: direccion IP del cliente.
- `connectedAt`: timestamp de conexión.

**Por qué**: estructura limpia e inmutable para manejar datos de conexión.

### 2. `UserConnectionConfig` (componente Spring)
**Ubicación**: `com.arquitectura.servidor.business.user`

Lee la configuración desde `user.properties`:
- Propiedad: `server.max.users=5`
- Método: `getMaxUsers()` / `setMaxUsers(int)`

**Por qué**: centraliza la configuración y permite cambiarla sin recompilar.

### 3. `UserConnectionService` (servicio)
**Ubicación**: `com.arquitectura.servidor.business.user`

Gestiona conexiones y valida límite:
- `connect(username)`: agrega usuario si hay espacio, lanza excepción si se excede límite.
- `disconnect(userId)`: remueve usuario.
- `getActiveConnections()`: lista de conexiones activas (inmutable).
- `getCurrentConnectionCount()` / `getMaxConnections()`: estado actual.

**Integración Observer**: emite eventos `CONEXION`, `DESCONEXION`, `CONEXION_RECHAZADA` y `CONEXION_HILO` al `ServerActivitySource`.

**Por qué**: centraliza lógica de conexión y comunica cambios via Observer para que la consola vea todo en tiempo real.

### 4. `ObjectPool`, `ConnectionWorkerPool` y `PooledConnectionWorker`
**Ubicación**: `com.arquitectura.servidor.business.user`

Implementan el patron Object Pool:
- `ObjectPool<T>`: contrato de prestar/devolver objetos.
- `ConnectionWorkerPool`: pool concreto de workers reusables.
- `PooledConnectionWorker`: worker que procesa una conexion dentro de un hilo.

**Por que**: evita crear objetos de trabajo sin control y permite reusar workers para nuevas conexiones.

### 5. `UserConnectionLimitExceededException`
**Ubicación**: `com.arquitectura.servidor.business.user`

Excepción lanzada cuando se intenta conectar usuario pero el límite está completo.

**Por qué**: distingue errores de límite de otros errores para mejor manejo.

### 6. `ConnectedClientInfo` y `ClientConnectionQueryService`
**Ubicación**: `com.arquitectura.servidor.business.user` y `com.arquitectura.servidor.business.infrastructure`

Exponen servicios para clientes:
- Cantidad de clientes conectados.
- Listado de clientes conectados con IP, fecha y hora de inicio.
- Formato de respuesta para transmision: JSON.

### 7. `UserConnectionDemoRunner` (CommandLineRunner)
**Ubicación**: `com.arquitectura.servidor.presentation.console`

Simula conexiones/desconexiones al arrancar el servidor para demo.

**Por qué**: visualizar la funcionalidad sin cliente real.

## Flujo de Funcionamiento

1. **Arranque**: `ServerConsoleRunner` registra observador.
2. **Pool**: `UserConnectionService` toma un worker del `ConnectionWorkerPool`.
3. **Hilo**: se crea un thread por conexion para ejecutar `PooledConnectionWorker`.
4. **Demo**: `UserConnectionDemoRunner` intenta conectar 6 usuarios (5 exito, 1 rechazado).
5. **Consulta**: clientes pueden solicitar cantidad y listado de conectados (IP, fecha y hora inicio).
6. **Observación**: cada evento se emite via `ServerActivitySource`.
7. **Consola**: `ConsoleActivityObserver` imprime todos los eventos en tiempo real.

## Archivos de Configuración

### `user.properties` (nuevo)
```properties
server.max.users=5
```

Cambiar este valor para ajustar límite de usuarios simultáneamente conectados.

## Pruebas Nuevas

`UserConnectionServiceTest`: 10 pruebas unitarias.
- Conexión bajo límite.
- Múltiples conexiones hasta límite.
- Rechazo cuando se excede.
- Desconexión y reasignación de espacio.
- Inmutabilidad de lista de conexiones.
- Validación de configuración.
- Reuso de workers del pool.
- Verificacion de hilo por conexion.
- Servicio de conteo de clientes conectados.
- Servicio de listado con IP, fecha y hora.

`ConnectionWorkerPoolTest`: 4 pruebas unitarias para el Object Pool.

`ClientConnectionQueryServiceTest`: 2 pruebas unitarias para la exposicion de servicios a clientes.

Respuestas JSON de servicios:
```json
{"service":"CONNECTED_CLIENTS_COUNT","connectedClients":2}
```

```json
{"service":"CONNECTED_CLIENTS_LIST","clients":[{"ipAddress":"10.1.1.1","startDate":"2026-04-14","startTime":"13:31:43"}]}
```

## Cómo Modificar el Límite

Edita `src/main/resources/user.properties`:
```properties
server.max.users=10
```
Ejecuta el servidor y verás cómo permite hasta 10 conexiones simultáneamente.

