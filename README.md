# AGENDAPP: plataforma multiestablecimiento de servicios por cita

**Programación Avanzada · Grupo 3 · Equipo 5 · FET · 2026**

Implementación de la arquitectura aprobada: cuatro microservicios con APIs REST, comunicación asíncrona por eventos con Kafka, persistencia independiente en PostgreSQL y un gateway GraphQL de consulta.

> **¿Primera vez que lo corres, o vas a sustentar?** Lee [`COMO_PROBAR.md`](COMO_PROBAR.md). Trae el arranque paso a paso y un recorrido completo del sistema puesto en el lugar de la clienta, señalando qué ocurre por dentro en cada acción.
>
> **¿Vas a probar las APIs?** En [`postman/`](postman/) está la colección, con un flujo de quince peticiones que encadena solo los identificadores. No hay que copiar ningún UUID a mano.

---

## Qué levanta

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| gateway-graphql | 8000 | Punto de entrada único y panel de operación. Agrega información de varios servicios |
| ms-agenda | 8080 | Disponibilidad y ciclo de vida de la cita. Único productor de eventos |
| ms-establecimientos | 8081 | Establecimientos, profesionales y catálogo de servicios |
| ms-operaciones | 8082 | Inventario de insumos y liquidación de ingresos |
| ms-relacion-cliente | 8083 | Recurrencia, valoraciones y notificaciones |
| simulador | 8084 | Genera actividad sintetica llamando a la API de ms-agenda |
| frontend | 3000 | Aplicación React que consume el gateway |
| kafka-ui | 8090 | Interfaz gráfica de Kafka (herramienta de desarrollo) |
| postgres | 5432 | Un esquema independiente por servicio |
| kafka | 9092 | Bus de eventos en modo KRaft, sin ZooKeeper |

---

## Cómo ejecutar

Requiere únicamente **Docker Desktop**. No hace falta instalar Java ni Maven: se compila dentro de los contenedores.

```bash
docker compose up --build
```

La primera vez tarda varios minutos porque descarga Maven y las dependencias de los seis servicios Java y compila el frontend (medido en una carpeta limpia: unos 7 minutos hasta devolver el control, casi todo construyendo imágenes; después, entre 11 y 21 s hasta el `Started` de cada servicio). Está listo cuando cada servicio imprime `Started ... in N seconds`.

Para detener: `Ctrl+C` y luego `docker compose down`. Eso conserva los datos; `docker compose down -v` los borra (ver el aviso de abajo).

### Antes de levantar: apaga cualquier otra copia

`docker-compose.yml` fija los puertos publicados (3000, 5432, 8000, 8080–8084, 8090, 9092) y los `container_name` (`agenda-postgres`, `agenda-kafka`, `ms-agenda`, etc.). Por eso **no pueden convivir dos copias**, aunque usen `-p` con otro nombre de proyecto: la segunda falla por puerto ocupado o por nombre de contenedor repetido. Apaga la otra con `docker compose down` en su carpeta antes de levantar esta.

### Los datos viven en un volumen

PostgreSQL guarda sus datos en el volumen `agenda-data`, que Compose nombra con el del proyecto (por defecto, el de la carpeta). Una carpeta nueva parte con un volumen vacío; una carpeta que ya se levantó antes conserva sus datos entre arranques.

- **No hace falta `down -v` para el primer arranque.** Una carpeta limpia crea su propio volumen vacío y `db-init/` crea los cuatro esquemas.
- **`down -v` borra los datos de esa carpeta.** Úsalo solo si quieres empezar de cero a propósito y no hay nada que conservar.
- Caso particular: si el volumen es de una entrega anterior, que solo tenía el esquema `agenda`, `db-init/` no se vuelve a ejecutar sobre un volumen que ya existe y los esquemas nuevos no se crean. Ahí sí hay que recrear el volumen (`down -v`), sabiendo que se pierden sus datos.

---

## Cómo comprobar que funciona

### Opción rápida: el guion de demostración

```bash
chmod +x demo.sh
./demo.sh
```

Recorre el flujo completo y muestra el resultado de cada paso: crea el establecimiento, la profesional, el insumo y el servicio, reserva una cita, la cierra, y consulta qué hicieron los consumidores por su cuenta.

### El simulador

Con el compose levantado, el simulador genera una cita cada 15 segundos y la cierra, la cancela o la deja agendada. Así los cuatro topics reciben tráfico sin que haya que lanzar nada a mano.

Su estado se consulta en http://localhost:8084/estado, que devuelve el `establecimientoId` sobre el que trabaja. Se apaga poniendo `SIMULADOR_ACTIVO: "false"` en `docker-compose.yml`.

### Qué hay que ver

Al cerrar la cita, `ms-agenda` publica **un solo evento** y ocurren cuatro cosas que nadie pidió:

1. `ms-operaciones` registra el ingreso de 45.000
2. `ms-operaciones` descuenta el frasco de esmalte y, al llegar al umbral, publica `inventario.insumo-bajo`
3. `ms-relacion-cliente` registra la solicitud de valoración
4. `ms-relacion-cliente` programa el recontacto de la clienta a 28 días

Los dos servicios no se conocen entre sí, tienen grupos de consumo distintos y ninguno hizo esperar a la profesional: el cierre respondió de inmediato.

### Ver los eventos en Kafka

Abran **http://localhost:8090**, entren a *Topics* y verán `citas.agendada`, `citas.completada` e `inventario.insumo-bajo`. Al entrar a un topic y pulsar *Messages* se ve el JSON de cada evento con su clave de partición, que es el identificador del establecimiento.

### Ver las APIs

Cada servicio documenta su contrato OpenAPI y permite probarlo desde el navegador:

- http://localhost:8080/swagger-ui.html — ms-agenda
- http://localhost:8081/swagger-ui.html — ms-establecimientos
- http://localhost:8082/swagger-ui.html — ms-operaciones
- http://localhost:8083/swagger-ui.html — ms-relacion-cliente

### El panel de operación

> **Atención:** desde que el panel del establecimiento exige inicio de sesión, este panel estático **ya no recibe los datos protegidos**: pide ingresos, inventario y notificaciones sin token y muestra «Error al consultar: Inicia sesion para continuar» con los indicadores en «—». Para ver esa información en vivo usa el panel React, http://localhost:3000/negocio (ver [Inicio de sesión del establecimiento](#inicio-de-sesión-del-establecimiento)). Está registrado como limitación conocida; lo que sigue describe cómo se diseñó.

En **http://localhost:8000** el gateway sirve un panel de una sola página que muestra en vivo los ingresos liquidados, el inventario con sus alertas, las notificaciones generadas y la agenda. Se actualiza solo cada cinco segundos, así que con el simulador encendido se ven los números moverse.

Todo el panel se alimenta de **una sola consulta GraphQL**, que el gateway resuelve internamente contra cuatro servicios. El desplegable del pie muestra esa consulta tal cual se envía.

No depende de internet: no carga nada desde un CDN. Y al servirse desde el mismo origen que la API, no necesita configuración de CORS.

Sirve para tres cosas: probar el sistema mientras se desarrolla, demostrarlo sin tener que copiar identificadores entre pestañas, y como especificación funcional si el frontend se reconstruye con un framework.

### Probar el gateway GraphQL

En **http://localhost:8000/graphiql**. Las consultas protegidas (`inventario`, `resumenIngresos`, `notificaciones`, `clientesInactivos`, `cerrarCita`) piden primero la mutación `login` y la cabecera `Authorization: Bearer <token>` en la pestaña *Headers*. Esta consulta, en cambio, es pública, y es la que justifica que el gateway exista: la cita viene de `ms-agenda` y el nombre del servicio de `ms-establecimientos`, pero el cliente hace una sola petición.

```graphql
query {
  agenda(
    profesionalId: "PEGA_AQUI_EL_ID"
    desde: "2026-01-01T00:00:00-05:00"
    hasta: "2026-12-31T23:59:59-05:00"
  ) {
    inicio
    estado
    servicio
    duracionMinutos
    valorCobrado
  }
}
```

---

## Pruebas

Hay pruebas automáticas en **cuatro** módulos. `ms-establecimientos`, `simulador` y `frontend` **no tienen pruebas**.

| Módulo | Pruebas | Resultado medido el 2026-11-19 |
|---|---|---|
| `ms-agenda` | `CitaControllerTest` (17) y `PublicacionEventosTest` (2) | 19 / 19 |
| `ms-operaciones` | `FlujoEventosTest` | 3 / 3 |
| `ms-relacion-cliente` | `ClientesRestTest` (9) y `GeneradorRecordatoriosTest` (16) | 25 / 25 |
| `gateway-graphql` | `EmisorJwtTest` | 7 / 7 |

**¿Hace falta Maven?** Para correrlas **directamente en tu equipo, sí**: JDK 21 y Maven (el repositorio no trae Maven Wrapper):

```bash
cd ms-agenda && mvn test
cd ../ms-operaciones && mvn test
cd ../ms-relacion-cliente && mvn test
cd ../gateway-graphql && mvn test
```

**Sin instalar Java ni Maven**, con Docker y la misma imagen que usan los Dockerfile (`maven:3.9-eclipse-temurin-21`), por módulo:

```bash
docker run --rm -v "$PWD/ms-agenda":/app -w /app maven:3.9-eclipse-temurin-21 mvn test
```

(En PowerShell, `${PWD}` en lugar de `$PWD`.) *No verificado: este comando con Docker no se ejecutó; los resultados medidos de arriba salieron con Maven instalado en el equipo.* Sus logs están en [`docs/evidencias/`](docs/evidencias/).

Las pruebas **no necesitan que el compose esté levantado**: usan H2 en lugar de PostgreSQL y, donde hace falta, `@EmbeddedKafka` (un broker que arranca dentro de la JVM del test, basado en ZooKeeper, no en KRaft). Por eso no prueban la restricción `UNIQUE` en PostgreSQL real ni el broker del compose. Detalle en [`docs/pruebas-y-evidencias.md`](docs/pruebas-y-evidencias.md).

Algunas pruebas de `ms-agenda` y `ms-operaciones`:

| Prueba | Qué verifica |
|---|---|
| `reservarPublicaElEvento` | Reservar publica en `citas.agendada` con la clave de partición correcta |
| `reservaRechazadaNoPublica` | Una reserva rechazada con 409 no genera evento |
| `procesaElEventoYPersiste` | El consumidor procesa el evento y la liquidación queda en la base |
| `esIdempotente` | El mismo evento entregado dos veces produce una sola liquidación |
| `eventoInvalidoVaAlDlt` | Un JSON roto acaba en `.DLT` sin tumbar al consumidor |

### Colección de Postman

En `postman/` está `Plataforma-de-citas.postman_collection.json`. Se importa en Postman arrastrando el archivo.

Cubre los endpoints de los cuatro microservicios, el simulador y el gateway GraphQL, repartidos en siete carpetas. La primera, **00 Flujo completo**, es una secuencia de quince peticiones que monta un establecimiento desde cero y recorre el ciclo entero: catálogo, receta de insumos, consulta de cupos, reserva, intento de sobrecupo, cierre y comprobación del efecto en los demás servicios.

Cada petición guarda en variables los identificadores que necesita la siguiente, y las fechas se recalculan en cada ejecución, así que la colección no caduca. Todas llevan pruebas automáticas: se puede ejecutar la carpeta completa con el *Collection Runner* y ver el resultado en verde.

> **Inicio de sesión en la carpeta del gateway.** *Inventario y resumen juntos*, *Notificaciones*, *Clientas inactivas* y *Mutación: cerrar* usan operaciones que exigen sesión. La primera petición de esa carpeta, **Login (guarda el token)**, entra con `admin@demo.com` / `demo1234` y guarda el token en la variable de colección `token`; esas cuatro lo envían en `Authorization: Bearer {{token}}`. Hay que ejecutar el login antes (el token dura 8 horas). *Mutación: cancelar* no lo necesita: envía `"por": "cliente"`, que es pública. La carpeta **00 Flujo completo** y el resto de la colección no cambian.

Dos peticiones están hechas para fallar a propósito, y son las que mejor explican el diseño:

- **08 Reservar la MISMA hora** debe devolver `409`. Demuestra que la concurrencia la resuelve la restricción `UNIQUE` de PostgreSQL, no Kafka.
- **Error a propósito** en la carpeta del gateway pide un campo inexistente, y GraphQL lo rechaza antes de llamar a ningún microservicio. Demuestra que el esquema es el contrato.

---

## Los eventos del sistema

| Topic | Productor | Grupos de consumo |
|---|---|---|
| `citas.agendada` | ms-agenda | relacion-cliente |
| `citas.completada` | ms-agenda | **operaciones**, **relacion-cliente** |
| `citas.cancelada` | ms-agenda | relacion-cliente |
| `inventario.insumo-bajo` | ms-operaciones | relacion-cliente |

`citas.completada` es el núcleo del diseño: un hecho, dos consumidores independientes. Agregar un tercero no obliga a tocar `ms-agenda`.

**Clave de partición:** el identificador del establecimiento. Garantiza que los eventos de un mismo negocio se procesen en orden.

La estructura completa de cada evento, campo por campo, está en **EVENTOS.md**.

**Semántica de entrega:** al menos una vez. Por eso los consumidores controlan idempotencia: `ms-operaciones` con la restricción `UNIQUE` sobre `cita_id` en liquidaciones, y `ms-relacion-cliente` con `UNIQUE (cita_id, tipo)` en notificaciones. Si el mismo evento llega dos veces, no se duplica nada.

---

## El frontend

En **http://localhost:3000**. Aplicación React construida con Vite y servida por nginx.

| Pantalla | Qué hace | Qué demuestra |
|---|---|---|
| Hoy | La jornada hora por hora, con lo reservado y lo libre | Una sola consulta que el gateway resuelve contra tres microservicios |
| Agenda | Ver todas las citas, cerrarlas y anularlas | Cerrar una cita publica `citas.completada` |
| Reservar | Elegir servicio, día y hora libre | Ataca el problema P2: disponibilidad consultable en cualquier momento |
| Operación | Ingresos e inventario, se refresca solo | El resultado de los eventos, sin que nadie lo registre a mano |
| Clientas | Quién no ha vuelto y los avisos generados (incluye los recordatorios), se refresca solo cada 4 s | El problema P6: seguimiento a la recurrencia |

Consume **únicamente el gateway GraphQL**, nunca las APIs REST de cada servicio. Esa es la razón de ser del gateway: la pantalla de inicio hace una sola consulta y recibe las citas con el nombre del servicio ya resuelto, los ingresos y el inventario, aunque eso viva en tres servicios distintos.

### Qué pasa cuando se cae un servicio

Esta es la parte del frontend que más vale la pena enseñar, y conviene provocarla a propósito durante la sustentación.

GraphQL responde `200` aunque algo falle, y devuelve las dos cosas a la vez: los campos que sí pudo resolver en `data`, y la lista de lo que falló en `errors`, cada uno con el `path` del campo afectado. El cliente de `src/api/graphql.js` no lanza excepciones: devuelve qué llegó, qué no, y de qué campo se trata.

Con eso, el gancho `useConsulta` aplica tres reglas:

1. **Conserva la última lectura buena.** Si el inventario deja de responder, se sigue mostrando lo último que llegó, con un sello ámbar discreto que dice cuándo fue. Es un dato que estuvo vigente hace un minuto, y es más útil que un hueco.
2. **Degrada por campo, no por pantalla.** La misma consulta puede traer la agenda y fallar el inventario: solo se marca el panel afectado.
3. **Reintenta sola, espaciando los intentos** (2 s, 4 s, 8 s y luego cada 15 s). Cuando el servicio vuelve, la pantalla se arregla sin que nadie recargue.

El usuario nunca ve un código HTTP, ni un nombre de clase de Java, ni la palabra *gateway*. Los mensajes técnicos se traducen en `src/api/mensajes.js` y el detalle original se escribe en la consola del navegador, que es donde lo busca quien programa.

Hay tres estados posibles y ninguno es una pantalla de error:

| Situación | Lo que ve la persona |
|---|---|
| El servicio cae con datos ya cargados | Los mismos datos, con el sello *Última lectura hace un momento* |
| El servicio ya estaba caído al abrir | Un panel tranquilo: *El inventario no está disponible ahora*, con un botón para reintentar |
| No hay gateway en absoluto | Una pantalla con la marca: *No encontramos el servidor*, reintentando sola |

### Desarrollar el frontend

```bash
cd frontend
npm install
npm run dev
```

Queda en http://localhost:5173 con recarga automática al guardar. Vite reenvía `/graphql` al gateway del puerto 8000, así que el backend tiene que estar levantado con `docker compose up`.

No usa Apollo ni librerías de estado: solo React, React Router y `fetch`. Las tipografías viajan dentro del proyecto en `public/fuentes/`, no se descargan de ningún servidor: la aplicación abre igual sin internet.

### Estructura

```
frontend/src/
├── api/          cliente GraphQL, consultas, mensajes y fechas
├── estado/       conexión y sesión del establecimiento, compartidas fuera de React
├── ganchos/      useConsulta (datos tolerantes a fallos), avisos, conexión, sesión
├── componentes/  barra lateral, paneles, iconos y piezas sueltas
├── contexto/     el salón y el profesional seleccionados
└── paginas/      una por pantalla
```

Cada pantalla es independiente, así que se pueden repartir entre varias personas sin pisarse.

---

## Inicio de sesión del establecimiento

La versión **cliente** (`/cliente`: reservar, Mis citas) es pública. El panel del **establecimiento** (`/negocio`: Hoy, Agenda, Operación, Clientas) exige sesión: sin ella, `/negocio` lleva a `/negocio/login`.

| Correo | Clave |
|---|---|
| `admin@demo.com` | `demo1234` |

Ese usuario de prueba se crea solo, unos segundos después de arrancar, ligado al establecimiento más antiguo que exista en ese momento. El establecimiento lo fija la sesión: en el panel ya no se elige.

> **Ojo: puede no ser el establecimiento del simulador.** En un arranque limpio medido el 2026-11-19 se crearon **dos** «Establecimiento de simulacion» y el admin quedó ligado al más antiguo, que **no** era el que usa el simulador. Con esa sesión el panel `/negocio` no muestra la actividad del simulador (`notificaciones` devolvió 0 filas).
>
> **Causa (inferida leyendo el código y el log; no reproducida por separado):** `SemillaDatos` del simulador crea establecimiento, profesional, servicio e insumo, y si **cualquier** paso falla repite todo desde el principio, sin comprobar lo ya creado. En ese arranque el log muestra un intento fallido en `POST /establecimientos` y otro en `POST /inventario` (ms-operaciones aún no respondía); este segundo ya había creado un establecimiento, y el intento siguiente creó otro. `SembradoUsuarios` elige el de menor `creadoEn`, o sea el del intento incompleto. Solo ocurre si algún servicio tarda en responder cuando arranca el simulador.
>
> **Cómo reconocerlo:** compara tres datos. 1) `GET http://localhost:8081/establecimientos` lista dos con el nombre «Establecimiento de simulacion». 2) `GET http://localhost:8084/estado` devuelve el `establecimientoId` del simulador. 3) `login` devuelve el `establecimientoId` del admin (también lo dice el log de `ms-establecimientos`: «Usuario de prueba admin@demo.com creado para el establecimiento …»). Si 2 y 3 no coinciden, estás en este caso. En la aplicación no hay forma de cambiar el establecimiento del admin; empezar de cero exige `docker compose down -v`, que **borra los datos**, así que hazlo solo si no hay nada que conservar.

**Cómo está repartido**

| Pieza | Responsabilidad |
|---|---|
| `ms-establecimientos` | Guarda los usuarios (tabla `establecimientos.usuario`, clave con BCrypt) y confirma credenciales en `POST /auth/verificar`. No emite tokens |
| `gateway-graphql` | Emite y valida el JWT (HS256). Mutación `login(correo, clave)`. Cada operación decide si exige sesión |
| `frontend` | Pantalla de login, ruta protegida, cierre de sesión, aviso *Tu sesión venció* y cierre entre pestañas |

**Qué exige sesión en el gateway**

| Operación | Acceso |
|---|---|
| `establecimientos`, `profesionales`, `servicios`, `disponibilidad`, `agenda`, `reservarCita`, `confirmarCita`, `login` | Pública |
| `inventario`, `resumenIngresos`, `notificaciones`, `clientesInactivos` | Sesión **y** que el establecimiento consultado sea el del token (si no, `FORBIDDEN`) |
| `cerrarCita` | Solo sesión |
| `cancelarCita` | Pública si `canceladaPor` es `"cliente"`; con cualquier otro valor exige sesión |

**`JWT_SECRET` y `JWT_MINUTOS`.** El gateway los lee del entorno con valores de desarrollo por defecto (un secreto que está en el repositorio, y 480 minutos = 8 horas). **`docker-compose.yml` no los pasa al contenedor**, así que una variable de tu terminal no llega. Para definirlos sin tocar el compose, crea junto a él un `docker-compose.override.yml` (Compose lo carga solo):

```yaml
services:
  gateway-graphql:
    environment:
      JWT_SECRET: "una-clave-larga-de-al-menos-32-caracteres"   # con menos, el gateway no arranca
      JWT_MINUTOS: "480"
  ms-establecimientos:
    environment:
      SEMILLA_USUARIO_ACTIVA: "false"   # apaga el usuario de prueba
```

y `docker compose up -d gateway-graphql ms-establecimientos`. Para probar el vencimiento, `JWT_MINUTOS: "1"`; al terminar, se borra esa línea y se vuelve a levantar el gateway. El detalle está en [`COMO_PROBAR.md`](COMO_PROBAR.md).

### Limitaciones conocidas del inicio de sesión

- **`agenda` es pública.** La usa también *Mis citas* del cliente. Quien conozca un `profesionalId` puede leer sus citas, con los `clienteId`.
- **`cerrarCita` solo exige sesión.** No comprueba el establecimiento: cualquier usuario con sesión puede cerrar cualquier cita.
- **`canceladaPor` lo envía el cliente.** Cualquiera puede mandar `"cliente"` y cancelar sin sesión.
- **Los puertos REST 8080–8083 siguen publicados** por el compose y no piden token: saltan el gateway y el login.
- **Sin límite de intentos** de login.
- **El token vive en `localStorage`**, expuesto a XSS.
- **El secreto de desarrollo está en el repositorio.** Hay que definir `JWT_SECRET` fuera de desarrollo.
- **Solo existe el usuario de prueba:** no hay creación de usuarios ni cambio o recuperación de clave.
- **El panel estático de `:8000` dejó de mostrar datos protegidos:** pide ingresos, inventario y notificaciones sin token y no tiene forma de iniciar sesión. Se dejó así a propósito, sin modificarlo; el panel React de `:3000/negocio` es el que funciona con sesión.
- **Un error de autenticación en `inventario`, `notificaciones` o `clientesInactivos` anula toda la respuesta** (`data: null`), porque el esquema declara esas listas como no nulas.

---

## Recordatorio y confirmación de la cita

La notificación de reserva promete *«Te recordaremos antes de la fecha»*. Esto lo cumple.

**Confirmar.** En *Mis citas* (`/cliente/mis-citas`), cada cita agendada muestra el botón **Confirmar** junto a *Anular*; al pulsarlo pasa a la marca **Confirmada**. En el panel, *Agenda* y *Hoy* muestran la etiqueta **Confirmada** en esa cita (se actualiza sola, en unos 4 s). La cita **sigue `agendada`**: no existe un estado nuevo. Lo único que cambia es una columna `confirmada_en` (nullable) en `agenda.cita`, que se crea sola al arrancar `ms-agenda`. La confirmación **no publica ningún evento de Kafka** y es idempotente (confirmar dos veces conserva la primera fecha).

| Pieza | Qué se añadió |
|---|---|
| `ms-agenda` | `POST /citas/{id}/confirmacion`, `GET /citas/proximas?desde&hasta&page&size`, campo `confirmadaEn` en la respuesta |
| `gateway-graphql` | Mutación `confirmarCita(citaId)` (**pública**, igual que `reservarCita`) y campo `confirmadaEn` en `Cita` y `CitaDetallada` |
| `ms-relacion-cliente` | Job `GeneradorRecordatorios` y los clientes REST `AgendaClient` y `EstablecimientosClient` |
| `frontend` | Botón y marca en *Mis citas*, etiqueta en *Agenda* y *Hoy*, refresco automático en *Clientas* |

**El recordatorio.** Un job de `ms-relacion-cliente` revisa cada `RECORDATORIOS_CADA_MS` (60 s por defecto, con una espera inicial de 30 s) las citas agendadas que están por llegar. A cada cita **no confirmada** le registra **una** notificación `recordatorio-cita` cuando entra en la ventana de su establecimiento, o sea cuando se cumple `ahora < inicio <= ahora + ventana`. La ventana es `ventanaConfirmacionHoras` de cada establecimiento (24 h por defecto). Una reserva hecha dentro de la ventana recibe su recordatorio en cerca de un minuto. El texto es *«Tu cita es el 18/11/2026 10:00 (hora de Colombia). Confirmala en Mis citas para asegurar tu cupo.»* y se ve en *Clientas* del panel como **Recordatorio**.

- **Está activo por defecto.** Para apagarlo: `RECORDATORIOS_ACTIVO=false` (por ejemplo en un `docker-compose.override.yml`, como el de `JWT_SECRET`) y `docker compose up -d --no-deps ms-relacion-cliente`. Se puede cambiar el ritmo con `RECORDATORIOS_CADA_MS` y `RECORDATORIOS_ESPERA_INICIAL_MS`.
- **La ventana solo decide CUÁNDO recordar.** No libera cupos, no anula citas y no exige confirmar: una cita sin confirmar sigue agendada.
- **No hay envío real.** Igual que el resto de notificaciones, el recordatorio solo queda registrado en la base (ver *Limitaciones conocidas*).
- **No duplica.** La idempotencia la da la base de datos (`UNIQUE (cita_id, tipo)`): repetir la pasada, reiniciar el servicio o correr dos copias no crea un segundo recordatorio. Si `ms-agenda` o `ms-establecimientos` no responden, la pasada avisa en el log y se reintenta en la siguiente.
- **La hora del texto** se escribe en Colombia (`-05:00`), la misma zona que usa `ms-agenda`.

**Única modificación a `docker-compose.yml`.** Se añadieron dos líneas al servicio `ms-relacion-cliente`: `AGENDA_URL: http://ms-agenda:8080` y `ESTABLECIMIENTOS_URL: http://ms-establecimientos:8081`. Son imprescindibles porque el job tiene que llamar a otros contenedores, y dentro de un contenedor `localhost` es él mismo; hasta ahora ese servicio no recibía ninguna dirección de otro servicio. Es el mismo patrón que ya usa `ms-operaciones` con `ESTABLECIMIENTOS_URL`. No cambia `depends_on` ni nada más.

### Limitaciones conocidas del recordatorio y la confirmación

- **Cualquiera con el `citaId` puede confirmar.** `confirmarCita` es pública (no hay registro de clientas), igual de abierta que `cancelarCita` con `"cliente"`.
- **Errores 404/409 salen como `INTERNAL_ERROR`.** Confirmar una cita que no existe (404) o que ya no está agendada (409) responde `INTERNAL_ERROR` en GraphQL, porque el gateway no traduce esos códigos. La pantalla muestra el mensaje genérico *«No pudimos confirmar tu cita»*. Ocurre igual con cancelar una cita ya anulada.
- **Pico inicial de recordatorios.** La primera pasada del job, en un entorno con citas ya dentro de la ventana (por ejemplo las del simulador), crea de golpe un recordatorio por cada una (una docena en la prueba). Después el ritmo baja a unos pocos por hora.
- **No se recuerda dos veces.** Si una cita se recordó y luego se confirma, el recordatorio ya registrado se conserva; y no hay un segundo aviso aunque se acerque más la hora.
- **No hay índice `(estado, inicio)`** en `agenda.cita` para `/citas/proximas`. Es aceptable con el volumen actual; conviene añadirlo si la tabla crece mucho.
- **El panel *Clientas* se refresca cada 4 s**, no es un canal en tiempo real.

---

## Sobre un frontend separado

El gateway ya admite peticiones desde los puertos de desarrollo habituales (3000, 5173 y 4200), configurado en `ConfiguracionWeb`. Un frontend construido aparte con React, Angular o Vite puede consumir `http://localhost:8000/graphql` sin tropezar con CORS.

La recomendación es que cualquier frontend consuma **el gateway**, no las APIs REST de cada servicio: esa es la razón de ser del gateway (RNF-12) y evita que el cliente tenga que conocer la dirección de cada microservicio.

La versión cliente no tiene autenticación, así que elige el establecimiento de forma explícita. El panel del establecimiento sí la tiene: el gateway acepta `Authorization: Bearer <token>` (la cabecera ya está permitida en `ConfiguracionWeb`), y un frontend aparte debe llamar a `login` y reenviar ese token. Ver [Inicio de sesión del establecimiento](#inicio-de-sesión-del-establecimiento).

---

## Decisiones de implementación

**El evento se publica después del commit.** `ms-agenda` no manda el evento a Kafka dentro de la transacción: lo anuncia con `ApplicationEventPublisher` y `PublicadorKafka` lo envía con `@TransactionalEventListener(AFTER_COMMIT)`. Si la reserva falla, nunca se publica nada. Publicarlo dentro de la transacción haría que los consumidores reaccionaran a citas que no existen.

**La reserva se decide en la base de datos, no en el bus.** La restricción `UNIQUE (profesional_id, inicio)` resuelve la concurrencia y devuelve `409`. El bus transporta hechos consumados, no decisiones.

**Cada servicio declara su propia copia del evento.** No hay librería compartida de eventos: eso obligaría a recompilar y desplegar todos los servicios a la vez, que es justo lo que la arquitectura asíncrona quiere evitar.

**Los eventos viajan como JSON en texto plano.** Serializar con el tipo Java incrustado acopla los servicios y además hace ilegibles los mensajes en Kafka UI.

**Los consumidores no atrapan excepciones.** Si el procesamiento falla, la excepción sube hasta el `DefaultErrorHandler`, que reintenta 3 veces y, si no lo logra, publica el mensaje en el topic `.DLT`. Atrapar el error dentro del consumidor haría que Kafka diera el mensaje por procesado y el evento se perdería.

**Hay una llamada REST entre servicios.** Cuando llega `citas.completada`, `ms-operaciones` consulta a `ms-establecimientos` qué insumos consume el servicio. El evento trae el hecho, no el catálogo.

---

## Limitaciones conocidas

- **Cancelar no libera el cupo (medido).** La restricción `UNIQUE (profesional_id, inicio)` no distingue el estado. Medido el 2026-11-19 con el sistema levantado: reservar las 10:00, cancelar (200, `cancelada`) y volver a reservar el mismo horario devolvió **409**, y `/disponibilidad` siguió sin ofrecer esa franja; por GraphQL el error sale como `INTERNAL_ERROR`. Evidencia: [`docs/evidencias/prueba-carpeta-limpia.txt`](docs/evidencias/prueba-carpeta-limpia.txt), §5.7. Está pendiente de corregir.
- **Las notificaciones se registran, no se envían.** Integrar WhatsApp Business API exige una cuenta aprobada y tiene costo. Está declarado fuera de alcance en el riesgo R5.
- **Autenticación parcial.** Solo el panel del establecimiento tiene inicio de sesión; la versión cliente es pública y no hay registro de clientes. El filtrado por token existe en `inventario`, `resumenIngresos`, `notificaciones` y `clientesInactivos`, pero no en `cerrarCita` ni en `agenda`. Los límites completos están en [Inicio de sesión del establecimiento](#inicio-de-sesión-del-establecimiento).
- **La periodicidad del recontacto es fija** (28 días, configurable por propiedad). Afinarla por servicio queda pendiente.

---

## Problemas frecuentes

**Un servicio no arranca y dice que el esquema no existe**
Ejecuten `docker compose down -v` y vuelvan a levantar. **Atención: `-v` borra los datos de la base.**

**`port is already allocated`**
Otro programa usa ese puerto. Si es otra copia de esta aplicación, apáguenla con `docker compose down` (los puertos y nombres de contenedor son fijos). Si es otro programa, cambien el número de la izquierda en `docker-compose.yml`, por ejemplo `"5433:5432"`.

**Kafka se reinicia en bucle**
Suele ser el `CLUSTER_ID` sobre datos viejos. `docker compose down -v` y levantar de nuevo (**borra los datos**).

**Los consumidores no reciben nada**
Verifiquen en Kafka UI que el topic existe y tiene mensajes. Desde un contenedor la dirección es `kafka:29092`; desde el equipo, `localhost:9092`.

---

## Equipo

Ana Sofía Claros Melo · Juan Ignacio Cuéllar Durán · Luis Miguel Paladínez Yáñez · Nicolás Alejandro Salas Castro · Santiago Andrés Muñoz Rincón

Docente: Diego Fernando Vargas Ramírez
