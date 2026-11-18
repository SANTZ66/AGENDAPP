# Estructura y contenido de los eventos

**Programación Avanzada · Grupo 3 · Equipo 5 · FET**

Este documento describe los eventos que el sistema publica realmente. Lo que está aquí se corresponde uno a uno con el código: los nombres de los topics salen de las anotaciones `@KafkaListener` y de las constantes del publicador.

---

## 1. Qué responsabilidad cumple Kafka aquí

Kafka no está porque lo pida el enunciado: resuelve un problema concreto del negocio.

Cuando la profesional cierra una cita, tienen que ocurrir cuatro cosas: registrar el ingreso, descontar los insumos, programar el recontacto de la clienta y pedirle una valoración. Las cuatro son independientes entre sí, ninguna aporta valor inmediato a quien pulsó el botón, y ninguna debe condicionar a las demás.

Resolverlas de forma síncrona tendría dos consecuencias malas: la profesional esperaría por trabajo administrativo mientras atiende, y el fallo de cualquiera de las cuatro impediría registrar que el servicio se prestó.

Con el bus de eventos, `ms-agenda` publica **un hecho** y sigue adelante. Quien tenga que reaccionar, reacciona.

**La regla que gobierna el diseño:** el bus transporta hechos consumados, no decisiones. Por eso la reserva de un cupo **no** pasa por Kafka: esa es una decisión que exige exclusión mutua inmediata y la resuelve la restricción `UNIQUE (profesional_id, inicio)` en PostgreSQL. El evento se publica después, cuando la reserva ya es irreversible.

---

## 2. Topics

| Topic | Productor | Grupos de consumo | Qué significa |
|---|---|---|---|
| `citas.agendada` | ms-agenda | `relacion-cliente` | Se reservó un cupo |
| `citas.completada` | ms-agenda | `operaciones`, `relacion-cliente` | Se prestó el servicio |
| `citas.cancelada` | ms-agenda | `relacion-cliente` | Se liberó un cupo |
| `inventario.insumo-bajo` | ms-operaciones | `relacion-cliente` | Un insumo llegó a su umbral |

A estos se suman los topics `.DLT` que se crean automáticamente cuando un mensaje no se puede procesar: `citas.completada.DLT`, `citas.agendada.DLT`, etc.

**`citas.completada` es el núcleo del diseño.** Un solo hecho con dos consumidores independientes, cada uno con su propio grupo y su propio offset. Ninguno sabe que el otro existe. Incorporar un tercero no obliga a modificar `ms-agenda`.

### Convención de nombres

`<entidad>.<hecho-en-pasado>`

El pasado es deliberado: un evento comunica algo que **ya ocurrió**. Un nombre en imperativo (`liquidar-cita`) sería una orden, y una orden acopla al productor con quien debe obedecerla.

### Clave de partición

**El identificador del establecimiento.**

Garantiza que todos los eventos de un mismo negocio caigan en la misma partición y se procesen en orden. Si mañana crece el número de establecimientos, la carga se reparte entre particiones sin perder ese orden dentro de cada negocio.

---

## 3. Estructura de los eventos de cita

Los tres topics de citas comparten la misma forma. Es una **foto de la cita en el momento del hecho**, no una referencia: el consumidor no necesita volver a preguntarle nada a `ms-agenda` para procesarlo. Ese es el punto del desacoplamiento.

```json
{
  "citaId": "703b754d-caba-45ae-a1d5-413be292c564",
  "establecimientoId": "11111111-1111-1111-1111-111111111111",
  "profesionalId": "22222222-2222-2222-2222-222222222222",
  "servicioId": "33333333-3333-3333-3333-333333333333",
  "clienteId": "44444444-4444-4444-4444-444444444444",
  "inicio": "2026-11-10T14:00:00Z",
  "fin": "2026-11-10T15:00:00Z",
  "estado": "completada",
  "valorCobrado": 45000,
  "motivo": null,
  "ocurridoEn": "2026-11-10T15:05:12.483Z"
}
```

| Campo | Tipo | Presente en | Para qué |
|---|---|---|---|
| `citaId` | UUID | siempre | Identifica el hecho. Es la llave de idempotencia |
| `establecimientoId` | UUID | siempre | Clave de partición y aislamiento entre inquilinos |
| `profesionalId` | UUID | siempre | A quién corresponde el servicio |
| `servicioId` | UUID | siempre | Con qué `ms-operaciones` consulta la receta de insumos |
| `clienteId` | UUID | siempre | Con qué `ms-relacion-cliente` acumula la recurrencia |
| `inicio`, `fin` | ISO-8601 | siempre | Franja atendida. `inicio` es la fecha del servicio en la liquidación |
| `estado` | texto | siempre | `agendada`, `completada` o `cancelada` |
| `valorCobrado` | decimal | solo en `completada` | Importe a liquidar. En los demás viaja en `null` |
| `motivo` | texto | solo en `cancelada` | Razón de la cancelación, opcional |
| `ocurridoEn` | ISO-8601 | siempre | Cuándo ocurrió el hecho, no cuándo se consumió |

### Por qué una sola forma para los tres topics

Podrían haberse definido tres estructuras distintas. Se eligió una sola porque todos los hechos de una cita describen la misma entidad, y porque así cada consumidor usa un único deserializador sin configuración adicional por topic. Los campos que no aplican viajan en `null`.

### Estructura de `inventario.insumo-bajo`

```json
{
  "insumoId": "55555555-5555-5555-5555-555555555555",
  "establecimientoId": "11111111-1111-1111-1111-111111111111",
  "nombre": "Esmalte semipermanente",
  "existencia": 8,
  "umbralMinimo": 8,
  "ocurridoEn": "2026-11-10T15:05:13.117Z"
}
```

Este evento demuestra que **un consumidor puede a su vez ser productor**: `ms-operaciones` consume `citas.completada`, descuenta el insumo y, si cruza el umbral, publica un hecho nuevo que `ms-relacion-cliente` recoge. La cadena ocurre sin que `ms-agenda` sepa nada de ella.

---

## 4. Formato de serialización

Los eventos viajan como **JSON en texto plano**, con `StringSerializer` en el productor y `StringDeserializer` en el consumidor.

Se descartó el serializador de Spring que incrusta el tipo Java en las cabeceras por dos razones: acopla a los servicios a las mismas clases, lo que obliga a desplegarlos juntos ante cualquier cambio; y vuelve los mensajes ilegibles al inspeccionarlos en Kafka UI.

Cada servicio declara **su propia copia** del record que representa el evento. La duplicación es deliberada: una librería compartida de eventos convertiría un cambio en ella en un redespliegue coordinado de todo el sistema, que es precisamente lo que la arquitectura asíncrona busca evitar. Los campos que un consumidor no reconoce se ignoran.

---

## 5. Qué hace cada consumidor

### Al recibir `citas.completada`

**`ms-operaciones`** (grupo `operaciones`)
1. Comprueba si esa cita ya fue liquidada. Si sí, la ignora.
2. Registra la liquidación con el valor cobrado.
3. Consulta por REST a `ms-establecimientos` qué insumos consume el servicio.
4. Descuenta cada insumo y deja un movimiento de inventario.
5. Si alguno cruza su umbral, publica `inventario.insumo-bajo`.

**`ms-relacion-cliente`** (grupo `relacion-cliente`)
1. Registra la solicitud de valoración.
2. Actualiza el historial del cliente y programa el recontacto a 28 días.

### Al recibir los demás

| Topic | Servicio | Acción |
|---|---|---|
| `citas.agendada` | ms-relacion-cliente | Registra la confirmación de la reserva |
| `citas.cancelada` | ms-relacion-cliente | Registra el aviso y suma una cancelación al historial |
| `inventario.insumo-bajo` | ms-relacion-cliente | Registra la alerta de reposición para la profesional |

Hay una llamada REST dentro del procesamiento de un evento, en el paso 3 de `ms-operaciones`. Es deliberado: **el evento transporta el hecho, no el catálogo**. Incluir la receta de insumos dentro del evento obligaría a `ms-agenda` a conocer el inventario, que no es asunto suyo.

---

## 6. Entrega e idempotencia

La semántica de Kafka es **al menos una vez**: un mensaje puede entregarse más de una vez, por ejemplo si el consumidor cae después de procesarlo pero antes de confirmar su offset. Por eso cada consumidor es idempotente.

| Dónde | Mecanismo |
|---|---|
| `ms-operaciones`, liquidaciones | `UNIQUE (cita_id)` más comprobación previa con `existsByCitaId` |
| `ms-relacion-cliente`, notificaciones | `UNIQUE (cita_id, tipo)` más comprobación previa |
| `ms-relacion-cliente`, recurrencia | `UNIQUE (establecimiento_id, cliente_id)` |

La restricción de base de datos es la garantía real; la comprobación previa solo evita el ruido de una excepción esperable.

### El evento se publica después del commit

`ms-agenda` no envía el evento dentro de la transacción. Lo anuncia con `ApplicationEventPublisher` y `PublicadorKafka` lo envía con `@TransactionalEventListener(AFTER_COMMIT)`.

Si la reserva falla, nunca se publica nada. Publicarlo dentro de la transacción haría que los consumidores reaccionaran a citas que acabaron sin existir. La prueba `reservaRechazadaNoPublica` verifica exactamente esto.

---

## 7. Manejo de errores

Configurado en `ManejoDeErroresKafka` de cada servicio consumidor.

| Situación | Qué ocurre |
|---|---|
| Fallo temporal (base de datos caída, servicio no responde) | Se reintenta 3 veces esperando 2 segundos entre intentos |
| Siguen fallando los reintentos | El mensaje se publica íntegro en `<topic>.DLT` |
| JSON malformado | Va directo al `.DLT` sin gastar reintentos |

La distinción entre errores recuperables y no recuperables importa: reintentar un JSON roto diez veces da el mismo resultado diez veces.

**Los consumidores no atrapan excepciones.** Es deliberado y contraintuitivo: atrapar el error y seguir significaría dar el mensaje por procesado cuando no lo fue, y Kafka avanzaría el offset dejando el evento perdido para siempre. Al dejar que la excepción suba, el manejador puede reintentar o preservar el mensaje.

### Logs

Cada consumidor registra la recepción del evento con su `citaId`, el resultado del procesamiento y, si lo hubo, el fallo con su causa. Los reintentos se registran con el topic, la partición y el offset del mensaje, de modo que se puede localizar en Kafka UI.

---

## 8. Pruebas

En `ms-agenda/src/test` y `ms-operaciones/src/test`. Usan `@EmbeddedKafka`: un broker real que arranca dentro de la misma JVM del test, sin necesitar Docker.

```bash
mvn test
```

| Prueba | Qué verifica |
|---|---|
| `PublicacionEventosTest.reservarPublicaElEvento` | Reservar publica en `citas.agendada` con la clave de partición correcta |
| `PublicacionEventosTest.reservaRechazadaNoPublica` | Una reserva que recibe 409 no genera evento |
| `FlujoEventosTest.procesaElEventoYPersiste` | El consumidor procesa el evento y la liquidación queda en la base |
| `FlujoEventosTest.esIdempotente` | El mismo evento dos veces produce una sola liquidación |
| `FlujoEventosTest.eventoInvalidoVaAlDlt` | Un JSON roto acaba en `.DLT` sin tumbar al consumidor |

---

## 9. Decisiones pendientes

- **Reproceso desde el DLT.** Hoy el mensaje queda guardado pero hay que reinyectarlo a mano. Falta decidir si se automatiza.
- **Esquema versionado de eventos.** Con un solo productor no ha hecho falta. Si crecen los consumidores, habría que versionar el contrato del evento.
- **Confirmación manual de offsets.** Hoy se usa la confirmación automática de Spring Kafka, suficiente porque los consumidores son idempotentes.
