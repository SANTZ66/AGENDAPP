# Referencia de la API

Leída de los controladores, los DTO y de `gateway-graphql/src/main/resources/graphql/schema.graphqls`. Lo que no se pudo comprobar en el código figura como **no verificado**. Contexto general: [manual-tecnico.md](manual-tecnico.md).

Convenciones:

- Los identificadores son UUID. Las fechas con hora son ISO-8601 con zona (`2026-10-15T09:00:00-05:00`). En las URL conviene enviarlas en UTC con `Z`, porque un `+` en el offset se pierde (lo advierte `CitaController`).
- Ningún microservicio REST exige autenticación (ver manual, sección 6). La sesión (JWT) solo existe en el gateway GraphQL.
- Swagger UI (`/swagger-ui.html`) está configurado en ms-agenda, ms-establecimientos, ms-operaciones y ms-relacion-cliente (springdoc). El contrato de ms-agenda también está en `ms-agenda/openapi/ms-agenda.yaml` (no se comparó con el código).
- Formato de error de ms-agenda y ms-establecimientos (`ErrorResponse`): `{ timestamp, status, error, mensaje, detalles }`, donde `detalles` es un mapa `campo → mensaje` en los 400 de validación. ms-operaciones devuelve ese mismo formato solo en los 400 de validación. Los 404 de ms-operaciones y ms-relacion-cliente salen de `ResponseStatusException` con el cuerpo estándar de Spring (su forma exacta: **no verificado**).

## 1. ms-agenda (puerto 8080)

### `POST /citas` → 201
Reserva una cita (transaccional; publica `citas.agendada`).

Cuerpo (`ReservarCitaRequest`), todos obligatorios:

```json
{
  "establecimientoId": "uuid", "profesionalId": "uuid", "servicioId": "uuid",
  "clienteId": "uuid", "inicio": "2026-10-15T09:00:00-05:00", "fin": "2026-10-15T10:00:00-05:00"
}
```

Respuesta: `CitaResponse` = `{ id, establecimientoId, profesionalId, servicioId, clienteId, inicio, fin, estado, valorCobrado, confirmadaEn }`, con `estado` en minúscula (`agendada`, `completada`, `cancelada`); `confirmadaEn` es `null` al nacer.
Errores: `400` falta un campo (detalles por campo); `409` el profesional ya tiene una cita con ese `inicio` (restricción `cupo_unico`, también ante carreras).

### `GET /citas` → 200
Agenda de un profesional, página de `CitaResponse` (formato `Page` de Spring: `content`, `totalElements`, etc.), ordenada por `inicio` ascendente.
Parámetros: `profesionalId` (UUID, obligatorio), `desde` y `hasta` (fecha-hora, obligatorios), `page` (defecto 0), `size` (defecto 20). Falta un obligatorio → `400` (comportamiento por defecto de Spring).

### `GET /citas/proximas` → 200
Citas en estado **agendada** cuyo `inicio` cae en el rango, de cualquier profesional. Misma forma de página y orden.
Parámetros: `desde`, `hasta` (obligatorios), `page` (0), `size` (20). Sin fechas → `400`.

### `GET /disponibilidad` → 200
Lista (no paginada) de `{ inicio, fin }`: franjas de una hora entre 09:00 y 18:00 (offset -05:00) en las que el profesional no tiene una cita con ese inicio.
Parámetros: `profesionalId` (UUID), `fecha` (`LocalDate`, `2026-10-15`).
Nota: cuenta como ocupada **cualquier** cita con ese inicio, también una cancelada o completada, porque `existsByProfesionalIdAndInicio` no filtra por estado (aunque su comentario dice «activa») y la restricción `cupo_unico` tampoco. *Confirmado en ejecución el 2026-11-19 (ver `docs/evidencias/prueba-carpeta-limpia.txt`, §5.7).*

### `POST /citas/{citaId}/confirmacion` → 200
Sin cuerpo. La clienta confirma asistencia; fija `confirmadaEn` y es idempotente (conserva la fecha de la primera confirmación). **No publica evento.**
Errores: `404` cita inexistente; `409` si no está agendada.

### `POST /citas/{citaId}/cierre` → 200
Cuerpo: `{ "valorCobrado": 50000 }` (obligatorio, `>= 0`). Pasa a `completada` y publica `citas.completada`.
Errores: `400` valor ausente o negativo; `404`; `409` si no está agendada.

### `POST /citas/{citaId}/cancelacion` → 200
Cuerpo: `{ "canceladaPor": "cliente" | "profesional", "motivo": "texto opcional" }`. Pasa a `cancelada` y publica `citas.cancelada`. El resumen del endpoint dice «libera el cupo», pero el código no lo garantiza: ver la nota de disponibilidad (§1) y las limitaciones del manual.
Errores: `400` (`canceladaPor` ausente o fuera de los dos valores); `404`; `409` si no está agendada.

## 2. ms-establecimientos (puerto 8081)

### `POST /establecimientos` → 201
Cuerpo `NuevoEstablecimiento`: `nombre` (obligatorio, no vacío), `telefono` (opcional), `ventanaConfirmacionHoras` (opcional, `>= 1`; si falta, 24).
Respuesta `EstablecimientoDto`: `{ id, nombre, telefono, ventanaConfirmacionHoras }`. Error `400`.

### `GET /establecimientos` → 200
Lista de `EstablecimientoDto`.

### `GET /establecimientos/{establecimientoId}` → 200
`EstablecimientoDto`. `404` si no existe («Establecimiento {id} no existe»).

### `POST /establecimientos/{establecimientoId}/profesionales` → 201
Cuerpo: `{ "nombre": "..." }` (obligatorio). Respuesta `ProfesionalDto`: `{ id, establecimientoId, nombre, activo }`. Errores: `400`; `404` si el establecimiento no existe.

### `GET /establecimientos/{establecimientoId}/profesionales` → 200
Lista de `ProfesionalDto` (lista vacía si el establecimiento no existe o no tiene profesionales; no devuelve 404).

### `POST /servicios` → 201
Cuerpo `NuevoServicio`: `establecimientoId` (obligatorio), `nombre` (obligatorio), `duracionMinutos` (obligatorio, `>= 5`), `precio` (obligatorio, `>= 0`), `periodicidadDias` (opcional).
Respuesta `ServicioDto`: `{ id, establecimientoId, nombre, duracionMinutos, precio, periodicidadDias }`. Error `400`. No valida que el establecimiento exista.

### `GET /servicios?establecimientoId={uuid}` → 200
Catálogo del establecimiento (lista de `ServicioDto`). `establecimientoId` obligatorio.

### `GET /servicios/{servicioId}` → 200
`ServicioDto`; `404` si no existe. Lo consulta el gateway para resolver el nombre del servicio.

### `POST /servicios/{servicioId}/insumos` → 201
Cuerpo: `{ "insumoId": "uuid", "cantidadEstimada": 1.5 }` (ambos obligatorios; cantidad `> 0`). Respuesta: `{ insumoId, cantidadEstimada }`. Errores: `400`; `404` si el servicio no existe. No valida que el insumo exista (vive en otro servicio).

### `GET /servicios/{servicioId}/insumos` → 200
Receta: lista de `{ insumoId, cantidadEstimada }`. Lo consulta ms-operaciones.

### `POST /auth/verificar` → 200 / 401
Cuerpo: `{ "correo": "...", "clave": "..." }` (ambos obligatorios). Respuesta `UsuarioVerificado`: `{ usuarioId, nombre, establecimientoId, rol }`; nunca incluye la clave ni el hash. **No emite token.**
Errores: `400` campos vacíos; `401` («Correo o clave incorrectos») para correo inexistente, usuario inactivo o clave errónea.

## 3. ms-operaciones (puerto 8082)

### `POST /inventario` → 201
Cuerpo: `establecimientoId` (obligatorio), `nombre` (obligatorio), `unidad` (opcional), `existencia` (obligatorio), `umbralMinimo` (obligatorio).
Respuesta `InsumoDto`: `{ id, establecimientoId, nombre, unidad, existencia, umbralMinimo, enUmbral }`. Error `400`.

### `GET /inventario?establecimientoId={uuid}` → 200
Lista de `InsumoDto`.

### `PUT /inventario/{insumoId}` → 200
Cuerpo: `{ "existencia": n, "umbralMinimo": n }`; ambos opcionales (un campo ausente o `null` no se modifica). Respuesta `InsumoDto`. `404` si el insumo no existe.

### `GET /inventario/alertas?establecimientoId={uuid}` → 200
Insumos que alcanzaron el umbral (`existencia <= umbralMinimo`, según `Insumo.enUmbral`). Lista de `InsumoDto`.

### `GET /liquidaciones?establecimientoId=&desde=&hasta=` → 200
Parámetros obligatorios: `establecimientoId`, `desde`, `hasta` (fecha-hora). Lista de `{ id, citaId, profesionalId, servicioId, valor, fechaServicio }`. Filtra por `fechaServicio` (el `inicio` de la cita).

### `GET /resumen?establecimientoId=&desde=&hasta=` → 200
Mismos parámetros. Respuesta: `{ establecimientoId, desde, hasta, serviciosPrestados, totalIngresos }`.

Las liquidaciones **no se crean por REST**: nacen del evento `citas.completada`.

## 4. ms-relacion-cliente (puerto 8083)

### `GET /notificaciones?establecimientoId={uuid}` → 200
Lista de `{ id, citaId, tipo, canal, asunto, cuerpo, estado, creadaEn }`, de la más reciente a la más antigua. Tipos que genera el código: `confirmacion-reserva`, `aviso-cancelacion`, `solicitud-valoracion`, `alerta-insumo` (con `citaId` nulo) y `recordatorio-cita`. `canal` es siempre `simulado` y `estado` `registrada`.

### `GET /clientes/{clienteId}/recurrencia?establecimientoId={uuid}` → 200
`{ clienteId, ultimaVisita, proximoContacto, totalVisitas, totalCancelaciones }`. `404` si el cliente no tiene historial en ese establecimiento.

### `GET /clientes-inactivos?establecimientoId={uuid}` → 200
Lista de la misma forma: clientes con `proximoContacto` anterior a «ahora».

No hay endpoints de escritura: todo se alimenta por Kafka y por el job de recordatorios.

## 5. simulador (puerto 8084)

### `GET /estado` → 200
Mapa JSON: `activo`, `escenarioListo`, `establecimientoId`, `profesionalId`, `servicioId`, `citasGeneradas`. Es su único endpoint.

## 6. gateway-graphql (puerto 8000)

- Endpoint: `POST /graphql`. GraphiQL en `/graphiql` (habilitado en `application.properties`).
- Sesión: cabecera `Authorization: Bearer <token>` (el prefijo no distingue mayúsculas). El gateway **nunca rechaza** la petición por el token: cada operación decide si exige sesión.
- Las fechas son `String` ISO-8601.
- Los `ID` se convierten a UUID en el gateway; un valor que no sea UUID válido produce un error (código exacto: **no verificado**).

### Tipos

| Tipo | Campos |
|---|---|
| `Establecimiento` | `id`, `nombre`, `telefono`, `ventanaConfirmacionHoras` |
| `Profesional` | `id`, `establecimientoId`, `nombre`, `activo` |
| `Servicio` | `id`, `establecimientoId`, `nombre`, `duracionMinutos`, `precio`, `periodicidadDias` |
| `Cita` | `id`, `establecimientoId`, `profesionalId`, `servicioId`, `clienteId`, `inicio`, `fin`, `estado`, `valorCobrado`, `confirmadaEn` |
| `CitaDetallada` | `id`, `inicio`, `fin`, `estado`, `clienteId`, `servicio` (nombre), `duracionMinutos`, `valorCobrado`, `confirmadaEn` |
| `Insumo` | `id`, `establecimientoId`, `nombre`, `unidad`, `existencia`, `umbralMinimo`, `enUmbral` |
| `Resumen` | `establecimientoId`, `desde`, `hasta`, `serviciosPrestados`, `totalIngresos` |
| `Franja` | `inicio`, `fin` |
| `Recurrencia` | `clienteId`, `ultimaVisita`, `proximoContacto`, `totalVisitas`, `totalCancelaciones` |
| `Notificacion` | `id`, `citaId`, `tipo`, `canal`, `asunto`, `cuerpo`, `estado`, `creadaEn` |
| `SesionNegocio` | `token`, `expiraEn`, `nombre`, `establecimientoId`, `rol` |

### Queries

| Query | Argumentos (todos obligatorios) | Devuelve | ¿Exige sesión? | Resuelve |
|---|---|---|---|---|
| `establecimientos` | — | `[Establecimiento!]!` | No | `GET /establecimientos` |
| `profesionales` | `establecimientoId` | `[Profesional!]!` | No | `GET /establecimientos/{id}/profesionales` |
| `servicios` | `establecimientoId` | `[Servicio!]!` | No | `GET /servicios` |
| `agenda` | `profesionalId`, `desde`, `hasta` | `[CitaDetallada!]!` | No | `GET /citas` (`size=100`, solo la primera página) + `GET /servicios/{id}` por servicio distinto; si el servicio no se resuelve, `servicio` = «(desconocido)» |
| `disponibilidad` | `profesionalId`, `servicioId`, `fecha` | `[Franja!]!` | No | `GET /disponibilidad` (`servicioId` **no se reenvía**) |
| `inventario` | `establecimientoId` | `[Insumo!]!` | **Sí**, del mismo establecimiento | `GET /inventario` |
| `resumenIngresos` | `establecimientoId`, `desde`, `hasta` | `Resumen` | **Sí**, del mismo establecimiento | `GET /resumen` |
| `notificaciones` | `establecimientoId` | `[Notificacion!]!` | **Sí**, del mismo establecimiento | `GET /notificaciones` |
| `clientesInactivos` | `establecimientoId` | `[Recurrencia!]!` | **Sí**, del mismo establecimiento | `GET /clientes-inactivos` |

### Mutations

| Mutation | Argumentos | Devuelve | ¿Exige sesión? | Resuelve |
|---|---|---|---|---|
| `login` | `correo`, `clave` (obligatorios) | `SesionNegocio` | No (es la que abre la sesión) | `POST /auth/verificar` + emisión del JWT |
| `reservarCita` | `establecimientoId`, `profesionalId`, `servicioId`, `clienteId`, `inicio`, `fin` (todos obligatorios) | `Cita` | No | `POST /citas` |
| `confirmarCita` | `citaId` | `Cita` | No (idempotente) | `POST /citas/{id}/confirmacion` |
| `cerrarCita` | `citaId`, `valorCobrado: Float!` | `Cita` | **Sí** (cualquier sesión válida) | `POST /citas/{id}/cierre` |
| `cancelarCita` | `citaId`, `canceladaPor` (obligatorios), `motivo` (opcional) | `Cita` | **Solo si `canceladaPor` ≠ `"cliente"`**; con `"cliente"` no exige sesión | `POST /citas/{id}/cancelacion` (`motivo` nulo se envía como `""`) |

### Errores GraphQL

GraphQL responde HTTP 200 aunque falle una operación; el fallo viaja en la lista `errors`.

| Situación | Clasificación (`extensions.classification`) | Mensaje |
|---|---|---|
| Operación protegida sin token | `UNAUTHORIZED` | «Inicia sesion para continuar» |
| Operación protegida con token vencido | `UNAUTHORIZED` | «La sesion vencio, inicia sesion de nuevo» |
| Operación protegida con token alterado o mal formado | `UNAUTHORIZED` | «Inicia sesion para continuar» (el motivo `invalida` se trata como falta de sesión) |
| Sesión de otro establecimiento (`inventario`, `resumenIngresos`, `notificaciones`, `clientesInactivos`) | `FORBIDDEN` | «No tienes acceso a ese establecimiento» |
| `login` con credenciales incorrectas, usuario inactivo o campos vacíos | `UNAUTHORIZED` | «Correo o clave incorrectos» |
| Error de un microservicio (por ejemplo 404 o 409 de ms-agenda al confirmar, cerrar o cancelar; 409 por cupo ocupado al reservar) | `INTERNAL_ERROR` | El gateway **no traduce** esos códigos; lo documenta `README.md`. No reproducido en esta redacción |
| `ms-establecimientos` caído durante `login` | no manejado explícitamente en el código; **no verificado** | — |

Ejemplo de inicio de sesión y uso del token:

```graphql
mutation { login(correo: "admin@demo.com", clave: "demo1234") { token expiraEn establecimientoId } }
```

```
Authorization: Bearer <token>
```

Las credenciales son las de demostración sembradas por `SembradoUsuarios` (ver manual, sección 4).
