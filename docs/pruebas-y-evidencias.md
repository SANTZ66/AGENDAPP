# Pruebas y evidencias

Todo lo marcado **Medido** se ejecutó el 2026-11-19 con `mvn -B -ntp test` en cada módulo que tiene tests, sobre JDK 21.0.8 (Microsoft build) y Maven 3.9.9 (Maven Wrapper del usuario), en Windows 11, sin Docker. La salida completa de cada ejecución está en [docs/evidencias/](evidencias/), un archivo por servicio. Contexto técnico: [manual-tecnico.md](manual-tecnico.md).

## 1. Resumen medido

| Módulo | Clases de test | Resultado | Evidencia |
|---|---|---|---|
| ms-agenda | `CitaControllerTest`, `PublicacionEventosTest` | **19 / 19**, 0 fallos, 0 errores, 0 omitidos (BUILD SUCCESS, `exit=0`) | [ms-agenda-mvn-test.txt](evidencias/ms-agenda-mvn-test.txt) |
| ms-operaciones | `FlujoEventosTest` | **3 / 3** (BUILD SUCCESS) | [ms-operaciones-mvn-test.txt](evidencias/ms-operaciones-mvn-test.txt) |
| ms-relacion-cliente | `ClientesRestTest`, `GeneradorRecordatoriosTest` | **25 / 25** (BUILD SUCCESS) | [ms-relacion-cliente-mvn-test.txt](evidencias/ms-relacion-cliente-mvn-test.txt) |
| gateway-graphql | `EmisorJwtTest` | **7 / 7** (BUILD SUCCESS) | [gateway-graphql-mvn-test.txt](evidencias/gateway-graphql-mvn-test.txt) |
| **Total** | 6 clases | **54 / 54** | |

### La cifra de ms-agenda: 19 / 19, y de dónde sale el «17+3»

El resultado medido es **19 pruebas, 19 pasan** (`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`, línea 1977 del archivo de evidencia): 17 en `CitaControllerTest` (línea 355) y **2** en `PublicacionEventosTest` (línea 1973). La cifra de 19 reportada antes es correcta.

El «17+3» sale de contar anotaciones con `grep "@Test"`: en `PublicacionEventosTest` aparecen 3 coincidencias, pero solo 2 son `@Test` (líneas 87 y 121, métodos `reservarPublicaElEvento` y `reservaRechazadaNoPublica`). La tercera coincidencia es `@TestPropertySource` (línea 46), que contiene el texto «@Test» y no es una prueba. Por eso 17 + 2 = 19.

### Aclaraciones sobre el entorno de las pruebas

- `ms-agenda` y `ms-operaciones` usan **H2 en modo PostgreSQL**, no PostgreSQL real (`application-test.properties`). Las restricciones únicas se comprueban en H2.
- `PublicacionEventosTest` y `FlujoEventosTest` usan `@EmbeddedKafka` (broker embebido de `spring-kafka-test`; el log muestra servidor ZooKeeper embebido). **No es el Kafka 3.9.0 en modo KRaft del compose.**
- `CitaControllerTest` corre con la publicación de eventos apagada (`app.eventos.habilitados=false`).
- `FlujoEventosTest` apunta a `ms-establecimientos` en `localhost:19999`, donde no hay nada: no se probó el descuento de insumos con el catálogo real.
- `ClientesRestTest` usa un `com.sun.net.httpserver.HttpServer` local en lugar de ms-agenda y ms-establecimientos reales. `GeneradorRecordatoriosTest` usa Mockito (`org.mockito.Mockito`) para los colaboradores.
- El log de Maven muestra el modo en que cada clase terminó, no un resultado por método: el «obtenido» de cada caso es el de su clase (todas con 0 fallos).
- Los archivos de evidencia son la salida literal con **una sola edición**: el nombre de usuario del equipo se sustituyó por `USUARIO` (rutas `C:\Users\USUARIO\…` y `user.name=USUARIO`) antes de guardarlos.

## 2. Servicios sin tests

Comprobado en el árbol de archivos (`git ls-files`) y en `package.json`:

- **ms-establecimientos:** no tiene carpeta `src/test`. **Sin tests.**
- **simulador:** no tiene carpeta `src/test`. **Sin tests.**
- **frontend:** no tiene archivos de prueba ni script de `test` en `package.json` ni dependencias de pruebas. **Sin tests.**

Su comportamiento solo se ha ejercitado de forma indirecta o manual (por ejemplo, `ms-establecimientos` lo usa el simulador y `POST /auth/verificar` lo usa el gateway), pero **no hay una medición automatizada de ninguno de los tres**. Además, el login completo (`login` → ms-establecimientos → JWT) no tiene prueba automática: el test de `EmisorJwtTest` cubre solo el emisor del token.

## 3. Leyenda de estados

- **Medido:** se ejecutó el 2026-11-19 y el resultado está en `docs/evidencias/`. Entre paréntesis se indica el sustituto de infraestructura cuando lo hay.
- **Simulado:** el sistema lo ejercita el simulador de actividad dentro de `docker compose` (citas sintéticas cada 15 s, ver manual). **No se midió en esta ejecución** (no se levantó Docker), así que no se declara resultado.
- **Futuro:** no existe prueba o no se ha ejecutado nunca; es trabajo pendiente.

## 4. Matriz

### 4.1 ms-agenda · `CitaControllerTest` (17, H2, sin Kafka)

Evidencia: [ms-agenda-mvn-test.txt](evidencias/ms-agenda-mvn-test.txt) líneas 37 y 355 (`Tests run: 17, Failures: 0`).

| Caso (método) | Esperado | Obtenido | Estado | Evidencia |
|---|---|---|---|---|
| `reservar_devuelve201_yPersiste` | `POST /citas` → 201 y la cita queda guardada | Pasa (clase 17/17) | Medido (H2) | l. 355 |
| `reservar_sinCampos_devuelve400` | Sin campos obligatorios → 400 | Pasa | Medido (H2) | l. 355 |
| `reservar_mismoCupoDosVeces_devuelve409` | Mismo profesional y hora dos veces → 409 | Pasa | Medido (H2; la restricción única se evaluó en H2, no en PostgreSQL) | l. 355 |
| `reservar_laCitaNaceSinConfirmar` | `confirmadaEn` nulo al crear | Pasa | Medido (H2) | l. 355 |
| `cerrar_citaAgendada_devuelve200yCompletada` | Cierre → 200 y estado completada | Pasa | Medido (H2) | l. 355 |
| `cerrar_citaInexistente_devuelve404` | Cerrar cita inexistente → 404 | Pasa | Medido (H2) | l. 355 |
| `confirmar_citaAgendada_devuelve200ConFecha_ySigueAgendada` | Confirmar → 200, con fecha, sigue agendada | Pasa | Medido (H2) | l. 355 |
| `confirmar_esIdempotente_yConservaLaFechaOriginal` | Confirmar dos veces conserva la primera fecha | Pasa | Medido (H2) | l. 355 |
| `confirmar_citaInexistente_devuelve404` | → 404 | Pasa | Medido (H2) | l. 355 |
| `confirmar_citaCancelada_devuelve409` | → 409 | Pasa | Medido (H2) | l. 355 |
| `confirmar_citaCompletada_devuelve409` | → 409 | Pasa | Medido (H2) | l. 355 |
| `confirmar_noCambiaCerrarNiCancelar` | Confirmar no impide cerrar ni cancelar | Pasa | Medido (H2) | l. 355 |
| `proximas_devuelveSoloAgendadasDentroDelRango_enOrden` | Solo agendadas del rango, ordenadas | Pasa | Medido (H2) | l. 355 |
| `proximas_incluyeCitasDeCualquierProfesional` | No filtra por profesional | Pasa | Medido (H2) | l. 355 |
| `proximas_reflejaLaConfirmacion` | Se ve `confirmadaEn` | Pasa | Medido (H2) | l. 355 |
| `proximas_paginaLosResultados` | Paginación | Pasa | Medido (H2) | l. 355 |
| `proximas_sinFechas_devuelve400` | Sin fechas → 400 | Pasa | Medido (H2) | l. 355 |

### 4.2 ms-agenda · `PublicacionEventosTest` (2, H2 + Kafka embebido)

Evidencia: línea 356 y 1973 (`Tests run: 2, Failures: 0`).

| Caso | Esperado | Obtenido | Estado | Evidencia |
|---|---|---|---|---|
| Reservar una cita publica el evento `citas.agendada` | Un mensaje en el tópico tras confirmar la transacción | Pasa (clase 2/2) | Medido (Kafka embebido, no el del compose) | l. 1973 |
| Una reserva rechazada por cupo ocupado no publica ningún evento | Ningún mensaje adicional | Pasa | Medido (Kafka embebido) | l. 1973 |

### 4.3 ms-operaciones · `FlujoEventosTest` (3, H2 + Kafka embebido)

Evidencia: [ms-operaciones-mvn-test.txt](evidencias/ms-operaciones-mvn-test.txt) líneas 35 y 1681.

| Caso | Esperado | Obtenido | Estado | Evidencia |
|---|---|---|---|---|
| Un evento `citas.completada` queda liquidado en la base | Fila en `liquidacion` | Pasa (clase 3/3) | Medido (Kafka embebido, H2) | l. 1681 |
| El mismo evento entregado dos veces no duplica el ingreso | Una sola liquidación | Pasa | Medido (Kafka embebido, H2) | l. 1681 |
| Un evento con JSON inválido termina en `.DLT` y no se pierde | Mensaje en `citas.completada.DLT` | Pasa. El log muestra «Evento con formato invalido, va directo al DLT» (esperado en esta prueba) | Medido (Kafka embebido) | l. 1681 |

### 4.4 ms-relacion-cliente · `ClientesRestTest` (9, servidor HTTP falso)

Evidencia: [ms-relacion-cliente-mvn-test.txt](evidencias/ms-relacion-cliente-mvn-test.txt) líneas 29 y 34.

| Caso | Esperado | Obtenido | Estado | Evidencia |
|---|---|---|---|---|
| `agenda_recorreTodasLasPaginas_yEntiendeLaRespuestaReal` | El cliente de agenda lee todas las páginas | Pasa (clase 9/9) | Medido (servidor HTTP falso, no ms-agenda real) | l. 34 |
| `agenda_pideFechasEnUtc_conPaginacion` | Pide fechas en UTC con paginación | Pasa | Medido (servidor falso) | l. 34 |
| `agenda_conLaPrimeraPaginaVacia_devuelveVacio` | Lista vacía | Pasa | Medido (servidor falso) | l. 34 |
| `agenda_siElServidorFalla_devuelveVacio_sinLanzar` | Sin excepción ante error del servidor | Pasa | Medido (servidor falso) | l. 34 |
| `agenda_siNoHayServidor_devuelveVacio_sinLanzar` | Sin excepción si no hay servidor | Pasa | Medido (servidor falso) | l. 34 |
| `establecimientos_leeLaVentana_yIgnoraLosCamposQueNoUsa` | Lee la ventana e ignora campos extra | Pasa | Medido (servidor falso) | l. 34 |
| `establecimientos_conListaVacia_devuelveVacio` | Lista vacía | Pasa | Medido (servidor falso) | l. 34 |
| `establecimientos_siElServidorFalla_devuelveVacio_sinLanzar` | Sin excepción | Pasa | Medido (servidor falso) | l. 34 |
| `establecimientos_siNoHayServidor_devuelveVacio_sinLanzar` | Sin excepción | Pasa | Medido (servidor falso) | l. 34 |

### 4.5 ms-relacion-cliente · `GeneradorRecordatoriosTest` (16, Mockito)

Evidencia: líneas 35 y 65 (`Tests run: 16, Failures: 0`).

| Caso (método) | Esperado | Obtenido | Estado | Evidencia |
|---|---|---|---|---|
| `dentroDeLaVentana_registraElRecordatorio` | Registra el recordatorio | Pasa (clase 16/16) | Medido (colaboradores simulados) | l. 65 |
| `elTextoUsaLaHoraDeColombia_noLaDelServidor` | Texto en UTC-5 | Pasa | Medido (colaboradores simulados) | l. 65 |
| `fueraDeLaVentana_noRegistra` | No registra | Pasa | Medido (colaboradores simulados) | l. 65 |
| `elBordeDeLaVentana_cuentaPorDentro_ySeIncluyeSoloHastaElSegundoExacto` | Borde inclusivo en `ahora + ventana` | Pasa | Medido (colaboradores simulados) | l. 65 |
| `unaCitaQueYaEmpezo_noSeRecuerda` | No recuerda citas ya iniciadas | Pasa | Medido (colaboradores simulados) | l. 65 |
| `cadaEstablecimientoUsaSuPropiaVentana` | Ventana por establecimiento | Pasa | Medido (colaboradores simulados) | l. 65 |
| `consultaAAgendaConLaVentanaMasAncha` | Una consulta con la ventana máxima | Pasa | Medido (colaboradores simulados) | l. 65 |
| `unaCitaConfirmada_noGeneraRecordatorio` | Las confirmadas se omiten | Pasa | Medido (colaboradores simulados) | l. 65 |
| `siYaHayRecordatorio_seIgnora_yNoSeCreaOtro` | Sin duplicados | Pasa | Medido (colaboradores simulados) | l. 65 |
| `repetirLaVuelta_conLaBaseRealDeIdempotencia_noDuplica` | Repetir la vuelta no duplica | Pasa. El nombre habla de «base real»; no se verificó qué sustituto usa la prueba para la base | Medido (sustituto no verificado) | l. 65 |
| `siLaBaseRechazaElDuplicado_seCapturaYSigueConLasDemas` | Captura el duplicado y continúa | Pasa | Medido (colaboradores simulados) | l. 65 |
| `sinEstablecimientos_noConsultaCitas_ni_Registra` | No hace nada | Pasa | Medido (colaboradores simulados) | l. 65 |
| `sinCitas_noRegistraNada` | No registra | Pasa | Medido (colaboradores simulados) | l. 65 |
| `unaCitaDeUnEstablecimientoDesconocido_seIgnora` | Se ignora | Pasa | Medido (colaboradores simulados) | l. 65 |
| `elJobSoloExisteConActivoTrue` | El bean solo existe con `activo=true` | Pasa | Medido | l. 65 |
| `recordatorio_usaElOffsetDeColombia_aunqueLaFechaVengaEnOtraZona` | Offset -05:00 sin importar la zona de entrada | Pasa | Medido (colaboradores simulados) | l. 65 |

### 4.6 gateway-graphql · `EmisorJwtTest` (7, sin infraestructura)

Evidencia: [gateway-graphql-mvn-test.txt](evidencias/gateway-graphql-mvn-test.txt) líneas 29 y 30.

| Caso (método) | Esperado | Obtenido | Estado | Evidencia |
|---|---|---|---|---|
| `tokenValidDevuelveLaSesion` | Un token válido devuelve la sesión | Pasa (clase 7/7) | Medido | l. 30 |
| `tokenVencidoSeRechazaComoVencido` | Token vencido rechazado como vencido | Pasa | Medido | l. 30 |
| `firmaAlteradaSeRechaza` | Firma alterada rechazada | Pasa | Medido | l. 30 |
| `contenidoAlteradoSeRechaza` | Contenido alterado rechazado | Pasa | Medido | l. 30 |
| `tokenFirmadoConOtroSecretoSeRechaza` | Otro secreto rechazado | Pasa | Medido | l. 30 |
| `textoQueNoEsTokenSeRechaza` | Texto cualquiera rechazado | Pasa | Medido | l. 30 |
| `secretoCortoNoSeAcepta` | Secreto < 32 bytes rechazado | Pasa | Medido | l. 30 |

### 4.7 Lo que no se midió

| Caso | Esperado | Obtenido | Estado | Evidencia |
|---|---|---|---|---|
| Flujo completo con `docker compose up` (reserva → Kafka → liquidación, notificación, recurrencia) | Eventos consumidos por ambos grupos | **Parcial, medido en 4.8** (R-08): topics y lag 0. No se midió la liquidación ni la recurrencia | Medido (parcial) | [prueba-carpeta-limpia.txt](evidencias/prueba-carpeta-limpia.txt) §5.8 |
| Citas sintéticas del simulador cada 15 s | Citas creadas y cerradas/canceladas | No ejecutado | Simulado (sin medición) | — |
| Job de recordatorios en ejecución real (60 s) | Recordatorio registrado al entrar en la ventana | **Medido en 4.8** (R-05) | Medido | [prueba-carpeta-limpia.txt](evidencias/prueba-carpeta-limpia.txt) §5.6 |
| Siembra del usuario admin y login de extremo a extremo | `login` devuelve token | **Medido en 4.8** (R-02, R-03) | Medido | [prueba-carpeta-limpia.txt](evidencias/prueba-carpeta-limpia.txt) §5.2 |
| Reglas de acceso del gateway (`UNAUTHORIZED` / `FORBIDDEN`) sobre GraphQL | Código correcto por operación | **Parcial, medido en 4.8**: `UNAUTHORIZED` sin token y con clave mala. `FORBIDDEN` (otro establecimiento) y token vencido no se midieron | Medido (parcial) | [prueba-carpeta-limpia.txt](evidencias/prueba-carpeta-limpia.txt) §5.2 |
| Restricción `cupo_unico` en PostgreSQL real y reservas simultáneas | Una sola gana | No ejecutado (las pruebas usan H2, secuencial) | Futuro | — |
| Cancelar y volver a reservar la misma hora | Debería poder reservarse | **Medido en 4.8 (R-07): NO se puede, devuelve 409** | Medido (defecto confirmado) | [prueba-carpeta-limpia.txt](evidencias/prueba-carpeta-limpia.txt) §5.7 |
| Reintentos y DLT con Kafka real (KRaft 3.9.0) | Mensaje en `.DLT` tras 3 reintentos | No ejecutado con el broker del compose | Futuro | — |
| ms-establecimientos | — | **Sin tests** | Futuro | — |
| simulador | — | **Sin tests** | Futuro | — |
| frontend | — | **Sin tests** | Futuro | — |
| Carga y rendimiento | — | Ninguna prueba | Futuro | — |

### 4.8 Medido contra el sistema levantado (prueba de carpeta limpia)

Sistema construido desde el ZIP de `git archive` con `docker compose -p agendapp-prueba up --build -d` en Windows 11 / Docker 29.8.1, el 2026-11-19. Tiempos medidos: 425 s hasta que `up --build -d` devolvió el control (incluye la construcción de las imágenes), y 11–21 s más hasta el `Started` de cada servicio Spring. Evidencia completa: [prueba-carpeta-limpia.txt](evidencias/prueba-carpeta-limpia.txt). Todo con Postgres 16 y Kafka KRaft reales del compose, no H2 ni Kafka embebido.

| ID | Caso | Esperado | Obtenido | Estado |
|---|---|---|---|---|
| R-01 | Frontend :3000, Swagger de ms-agenda/establecimientos/operaciones/relacion-cliente, GraphiQL :8000, simulador `/estado`, Kafka UI :8090 | 200 | 200 en todos (`/swagger-ui.html` responde 302 y luego 200 al seguir la redirección) | Medido |
| R-02 | `login` con `admin@demo.com` / `demo1234` | Token y establecimiento | Token JWT, `expiraEn` ≈ 8 h, `establecimientoId` devuelto | Medido |
| R-03 | `login` con clave incorrecta | `UNAUTHORIZED` | HTTP 200 con `errors[0].extensions.classification = UNAUTHORIZED`, «Correo o clave incorrectos», `data.login = null` | Medido |
| R-04 | `notificaciones` sin token y con token | Sin token `UNAUTHORIZED`; con token datos | Sin token: `UNAUTHORIZED` «Inicia sesion para continuar», `data: null`. Con token: sin errores (0 filas en ese establecimiento) | Medido |
| R-05 | Recordatorio de una cita agendada a +3 h sin confirmar | Un `recordatorio-cita` | Apareció entre 15 y 30 s después de reservar; seguía en 1 tras 90 s y tras otros 75 s (varias pasadas del job). 1 recordatorio en total, 0 citas con más de uno | Medido |
| R-06 | `confirmarCita` y una segunda confirmación | Idempotente | Ambas devuelven la misma `confirmadaEn` (`2026-11-19T12:25:04.731764Z`), estado `agendada` | Medido |
| R-07 | Reservar, cancelar y reservar la misma franja (10:00) | Segunda reserva aceptada | Reservar 201; cancelar 200 (`cancelada`); `/disponibilidad` sigue sin ofrecer las 10:00; reservar de nuevo → **409 Conflict** (por GraphQL: `INTERNAL_ERROR`). **La cancelación no libera el cupo** | Medido: **defecto confirmado** |
| R-08 | Topics y grupos de Kafka | 4 topics, lag 0 | `citas.agendada`, `citas.cancelada`, `citas.completada`, `inventario.insumo-bajo`; ningún `.DLT`; lag 0 en todas las particiones de `operaciones` (1 partición) y `relacion-cliente` (4) | Medido |
| R-09 | Colección de Postman con newman | Ejecutar | **No se ejecutó**: newman no está instalado y no se instaló | No ejecutado |
| R-10 | README: admin ligado al establecimiento «del simulador» | Mismo establecimiento | **No coincide**: hay dos «Establecimiento de simulacion»; el admin quedó en `a97d2ec3…` y el simulador trabaja en `425f5a0e…`. Causa inferida del código y del log, no reproducida: `SemillaDatos.crear()` se repite entera si falla un paso; el intento 2 creó el establecimiento y falló en ms-operaciones, el siguiente creó otro; `SembradoUsuarios` toma el más antiguo | Medido (discrepancia con el README) |

Qué no cubre: `FORBIDDEN`, token vencido, cierre de cita → liquidación en ms-operaciones, recurrencia, y la restricción `cupo_unico` bajo concurrencia.

## 5. Cómo reproducir

Desde la carpeta de cada módulo (`ms-agenda`, `ms-operaciones`, `ms-relacion-cliente`, `gateway-graphql`):

```
mvn -B -ntp test
```

No necesita Docker ni Postgres: usa H2 y un Kafka embebido. Los archivos de `docs/evidencias/` se generaron así, con la redirección de la salida a texto.
