# Cómo correrlo y cómo probarlo

Programación Avanzada · FET · Grupo 3 · Equipo 5

Esta guía tiene dos partes. La primera es levantar el sistema. La segunda es
recorrerlo como si fueras la clienta del salón, viendo qué pasa por dentro en
cada paso.

---

## Parte 1. Levantar el sistema

### Antes de empezar

Necesitas Docker Desktop abierto y corriendo. Nada más. No hace falta tener
Java, ni Maven, ni Node instalados: todo se compila dentro de los contenedores.

### Si ya corriste el proyecto antes

Esto es importante, y es lo que te falló las dos veces anteriores. Los nombres
de contenedor son únicos en toda la máquina, no por carpeta. Si tienes una
copia vieja del proyecto levantada, el arranque falla con
`Conflict. The container name "/agenda-kafka" is already in use`.

Bórralos antes:

```bash
docker rm -f $(docker ps -aq --filter "name=agenda-") 2>/dev/null
docker rm -f $(docker ps -aq --filter "name=ms-") 2>/dev/null
docker rm -f gateway-graphql simulador frontend 2>/dev/null
docker volume prune -f
```

Y quédate con **una sola carpeta** del proyecto. Borra o renombra las demás.

### Arrancar

```bash
cd ruta/a/plataforma-citas
docker compose up --build
```

La primera vez tarda entre tres y cinco minutos, porque descarga las
dependencias de Maven y compila los cinco servicios Java.

**Esa terminal no vuelve a estar libre, y está bien.** `docker compose up` se
queda mostrando los logs de los diez contenedores para siempre. Eso no es que
esté cargando: es el sistema funcionando. Para trabajar, abre una pestaña
nueva de terminal.

Sabes que ya está listo cuando ves líneas del simulador apareciendo solas cada
quince segundos, del estilo:

```
ms-operaciones | Descontado 1.00 de Insumo simulado. Quedan 19
```

### Direcciones

| Dirección | Qué es |
|---|---|
| http://localhost:3000 | **El frontend.** Por aquí entras tú |
| http://localhost:8000 | Panel del gateway GraphQL |
| http://localhost:8090 | Kafka UI: tópicos, mensajes y consumidores |
| http://localhost:8080/swagger-ui.html | API de ms-agenda |
| http://localhost:8081/swagger-ui.html | API de ms-establecimientos |
| http://localhost:8082/swagger-ui.html | API de ms-operaciones |
| http://localhost:8083/swagger-ui.html | API de ms-relación-cliente |

### Para bajarlo

En la terminal de los logs, `Ctrl+C`. Y si quieres borrar también los datos:

```bash
docker compose down -v
```

### Credenciales

**Versión cliente** (`/cliente`): no hay. Es pública, sin inicio de sesión.

**Panel del establecimiento** (`/negocio`: Hoy, Agenda, Operación, Clientas):
exige inicio de sesión. Hay un usuario de prueba que se crea solo:

| Correo | Clave |
|---|---|
| `admin@demo.com` | `demo1234` |

Entra por http://localhost:3000/negocio (te lleva a `/negocio/login`). El
usuario queda ligado al establecimiento **más antiguo** de la base, que es el
que crea el simulador la primera vez. Aparece unos 10 segundos después de
arrancar, porque el establecimiento no existe hasta que el simulador lo crea.
Si entras muy pronto y la clave no funciona, espera un momento.

Postgres usa `citas` / `citas`, pero eso es entre contenedores, tú no lo
necesitas.

#### Cómo funciona, en dos líneas

`ms-establecimientos` guarda los usuarios (clave con BCrypt) y confirma quién
es la persona. El **gateway** emite y valida el token (JWT, firmado con
HS256). La sesión dura **8 horas** y no se renueva: al vencer, la pantalla
vuelve sola al login con el aviso *Tu sesión venció*.

#### `JWT_SECRET` y `JWT_MINUTOS`

Tienen valores por defecto que sirven para desarrollo, así que **no tienes que
hacer nada** para probar. Pero ojo: `docker-compose.yml` **no pasa** estas
variables al contenedor. Una variable de tu terminal no llega al gateway.

| Variable | Dónde se lee | Por defecto | Para qué |
|---|---|---|---|
| `JWT_SECRET` | gateway-graphql | un valor de desarrollo que está en el repositorio | Firma de los tokens. Mínimo 32 caracteres; con menos el gateway no arranca |
| `JWT_MINUTOS` | gateway-graphql | `480` (8 horas) | Duración de la sesión |
| `SEMILLA_USUARIO_ACTIVA` | ms-establecimientos | `true` | `false` apaga la creación del usuario de prueba |
| `SEMILLA_USUARIO_CORREO` / `SEMILLA_USUARIO_CLAVE` | ms-establecimientos | `admin@demo.com` / `demo1234` | Credenciales del usuario de prueba |

Para definirlas **sin tocar `docker-compose.yml`**, crea junto a él un archivo
`docker-compose.override.yml`. `docker compose up` lo lee solo:

```yaml
services:
  gateway-graphql:
    environment:
      JWT_SECRET: "pon-aqui-una-clave-larga-de-al-menos-32-caracteres"
      JWT_MINUTOS: "480"
  ms-establecimientos:
    environment:
      SEMILLA_USUARIO_ACTIVA: "false"
```

Luego `docker compose up -d gateway-graphql ms-establecimientos` y comprueba
que llegó:

```bash
docker inspect gateway-graphql --format '{{range .Config.Env}}{{println .}}{{end}}' | grep JWT
```

**Probar el vencimiento sin esperar 8 horas:** pon `JWT_MINUTOS: "1"` en ese
archivo, vuelve a levantar el gateway, entra al panel y no toques nada. A los
60 segundos vuelve solo al login con el aviso. Después **borra el archivo (o
esa línea)** y ejecuta `docker compose up -d gateway-graphql`. No se pierden
datos: las sesiones son tokens que no se guardan en el servidor.

Si cambias `JWT_SECRET`, todas las sesiones abiertas dejan de valer y cada
persona tiene que entrar de nuevo.

---

## Parte 2. Recorrerlo como clienta

Abre http://localhost:3000. Arriba a la derecha hay dos listas: establecimiento
y profesional. Elige el del simulador, que es el que ya tiene datos.

Haz esto en orden y con los logs a la vista en la otra ventana. La gracia está
en ver que cada acción tuya provoca cosas en servicios que nadie llamó.

### Paso 1. Pedir una cita

Ve a **Reservar**. Elige el servicio y la fecha de mañana, y pulsa *Ver cupos
libres*. Te salen las horas disponibles de ese profesional.

Lo que pasó por dentro: el frontend le pidió al gateway, el gateway le preguntó
a `ms-agenda`, y `ms-agenda` calculó la jornada menos las horas ya ocupadas.

Pulsa una hora. Queda reservada.

**Lo que tienes que entender de este paso:** esta petición *no* pasó por Kafka.
Fue una llamada directa y síncrona, porque necesitabas una respuesta inmediata
(sí o no hay cupo). Kafka no sirve para esto.

### Paso 1b. Confirmar la cita y ver el recordatorio

Ve a **Mis citas** (`/cliente/mis-citas`). Tu cita agendada muestra dos botones:
*Confirmar* y *Anular*. Pulsa **Confirmar**: sale el aviso *«Tu cita quedó
confirmada.»* y el botón se convierte en la marca **Confirmada**. El estado sigue
diciendo *Agendada*. No hace falta sesión: confirmar es público, como reservar.

En la pestaña del establecimiento (con sesión), **Agenda** muestra la etiqueta
*Confirmada* junto al servicio en unos 4 segundos, sin recargar.

**El recordatorio.** Si reservas una cita para mañana y **no** la confirmas,
en cerca de un minuto el establecimiento ve en **Clientas** un aviso
*Recordatorio · Recuerda tu cita · Tu cita es el … (hora de Colombia)…*. Esa
pantalla se refresca sola cada 4 segundos. Lo genera un job de
`ms-relacion-cliente` que está **activo por defecto**:

```bash
docker compose logs -f ms-relacion-cliente     # una línea «Recordatorios: N nuevos | …» por minuto
```

Una cita **confirmada** no recibe recordatorio. Para apagar el job:
`RECORDATORIOS_ACTIVO=false` (en un `docker-compose.override.yml`, como se explica
para `JWT_SECRET`). La ventana de cada establecimiento (24 h por defecto) solo
decide **cuándo** recordar: no libera cupos ni anula nada. El recordatorio queda
registrado, no se envía por WhatsApp ni correo.

### Paso 2. Probar que no hay sobrecupo

Abre **otra pestaña** del navegador en la misma página de Reservar, con el
mismo profesional y la misma fecha. Pulsa *Generar otro* para que seas otra
clienta distinta. Intenta la misma hora que acabas de tomar.

Te sale un error en rojo. El cupo está tomado.

**Esto es lo más importante del proyecto.** Quien rechazó la segunda reserva
fue PostgreSQL, con una restricción `UNIQUE (profesional_id, inicio)` que
devuelve un 409. No fue Kafka, no fue un bloqueo en el código Java, no fue una
cola. Si dos peticiones llegan en el mismo milisegundo, la base de datos deja
pasar una sola.

Si te preguntan *"¿y por qué no resolvieron la concurrencia con Kafka?"*, la
respuesta es: porque **por el bus viajan hechos consumados, no decisiones**.
Una reserva es una decisión que necesita respuesta ya. Una cita completada es
un hecho que ya ocurrió y del que otros se enteran después.

### Paso 3. Atender a la clienta

Desde aquí pasas al lado del establecimiento. Abre
http://localhost:3000/negocio en **otra pestaña** (mejor una ventana de
incógnito, para que la clienta y el establecimiento no compartan nada) y entra
con `admin@demo.com` / `demo1234`. Si dejas esa pestaña abierta mientras
reservas, verás aparecer sola la *Nueva reserva* en pocos segundos.

Ve a **Agenda**. Ahí está tu cita. Pulsa *Cerrar* y confirma el valor, que
viene prellenado con el precio del catálogo.

**Ahora sí mira los logs.** Vas a ver una cadena como esta:

```
ms-agenda          | Cita completada publicada
ms-operaciones     | Descontado 1.00 de Esmalte semipermanente. Quedan 7
ms-operaciones     | Alerta publicada: Esmalte semipermanente esta en umbral
ms-relacion-cliente| Recibido inventario.insumo-bajo -> Esmalte semipermanente
ms-relacion-cliente| Alerta de reposicion registrada
```

Cuenta cuántos servicios se movieron: tres. Y tú solo tocaste uno.

Nadie llamó a `ms-operaciones`. Nadie llamó a `ms-relación-cliente`. Ellos
estaban escuchando los tópicos de Kafka. Si mañana agregan un cuarto servicio
que también quiera enterarse de las citas completadas, se suscribe al tópico y
listo: no hay que modificar `ms-agenda`.

Ese es el argumento de toda la entrega.

### Paso 4. Ver el efecto

Ve a **Operación**. Sin hacer nada más:

- Los ingresos subieron.
- El contador de servicios prestados subió.
- El insumo bajó, y si cruzó el umbral la fila aparece en ámbar.

Esa pantalla se refresca sola cada cinco segundos.

El negocio real no tenía ninguna de estas dos cosas. No sabía cuánto había
facturado en el mes ni cuánto material le quedaba. Ahora las dos se arman
solas, sin que nadie las registre a mano, porque se alimentan de los eventos.

### Paso 5. Ver el seguimiento

Ve a **Clientas**. Están las notificaciones que se generaron solas, y la lista
de clientas que no han vuelto según la periodicidad del servicio que se
hicieron.

### Paso 6. Apagar un servicio a propósito

Este paso conviene provocarlo tú antes de que lo haga el profesor.

Con la app abierta en **Operación**, en otra terminal:

```bash
docker compose stop ms-operaciones
```

Mira la pantalla sin recargarla. No aparece ningún error. Los números y la
tabla siguen ahí, y en la esquina del panel sale un sello ámbar que dice
*Última lectura hace un momento*. Eso es exactamente lo que debe pasar: la
información que se ve estuvo vigente hace segundos, y es más útil que un
hueco vacío o un mensaje técnico.

Ahora ve a **Hoy**. La jornada se ve completa, porque las citas las resuelve
`ms-agenda`, que sigue en pie. Solo las dos tarjetas de ingresos y materiales
se apagan, y el panel *Por reponer* dice en castellano que esa información no
está disponible y ofrece reintentar.

Vuelve a encenderlo:

```bash
docker compose start ms-operaciones
```

En menos de quince segundos la pantalla se arregla sola. Nadie recargó nada.

**Por qué funciona:** GraphQL responde `200` aunque algo falle, y devuelve a
la vez los campos que sí resolvió y la lista de los que no, cada uno con el
nombre del campo afectado. El frontend usa ese dato para degradar solo la
parte afectada, conservar la última lectura buena del resto y reintentar con
esperas crecientes.

Si quieres enseñar el caso extremo, apaga el gateway con
`docker compose stop gateway-graphql`: sale una pantalla con la marca del
salón que dice *No encontramos el servidor*, y vuelve sola al encenderlo.

En ningún momento la persona que usa la app ve un código HTTP, un nombre de
clase de Java ni la palabra *gateway*. El detalle técnico está en la consola
del navegador, que es donde lo necesita quien programa.

### Paso 7. Mirar el bus por dentro

Abre http://localhost:8090 (Kafka UI).

En **Topics** verás los cuatro tópicos. Entra a `citas.completadas` y mira los
mensajes: ahí está el JSON del evento que publicaste en el paso 3.

En **Consumers** verás los grupos de consumidores con estado `STABLE`. Cada
grupo es un microservicio escuchando. Fíjate en el *lag*: si está en cero,
están al día.

Si quieres enseñar que los mensajes se reparten por negocio, mira la partición
de cada mensaje: la clave es el `establecimientoId`, así que todos los eventos
de un mismo salón caen en la misma partición y se procesan en orden.

---

## Parte 3. Probar las APIs con Postman

En la carpeta `postman/` está la colección. Impórtala en Postman con
*Import* y arrastrando el archivo.

Abre la carpeta **00 Flujo completo** y ejecuta las peticiones en orden, de la
01 a la 15. Cada una guarda sola los identificadores que la siguiente necesita,
así que no tienes que copiar ni un solo UUID a mano. Las fechas se recalculan
en cada ejecución.

El flujo monta un salón desde cero y recorre lo mismo que acabas de hacer en el
frontend, pero viendo las peticiones HTTP crudas:

- La **07** reserva y devuelve `201 Created`.
- La **08** intenta la misma hora y devuelve `409 Conflict`. Esta petición
  **tiene que fallar**: es la prueba de que no hay sobrecupo.
- La **10** cierra la cita. Ejecútala mirando los logs.
- La **11**, **12** y **14** muestran el efecto en los otros servicios.

Cada petición tiene pruebas automáticas, así que en la pestaña *Test Results*
ves en verde si pasó. Puedes correr la carpeta entera de una vez con el botón
*Run*.

Las otras carpetas tienen todos los endpoints de cada servicio por separado,
incluidas peticiones mal formadas a propósito para mostrar que devuelven `400`.

> **Inicio de sesión en la carpeta 06 Gateway GraphQL.** Cuatro peticiones
> usan operaciones que exigen sesión: *Inventario y resumen juntos*,
> *Notificaciones*, *Clientas inactivas* y *Mutación: cerrar*. Ejecuta primero
> **Login (guarda el token)**, la primera de la carpeta: entra con
> `admin@demo.com` / `demo1234`, guarda el token en la variable de colección
> `token` y esas cuatro lo envían en `Authorization: Bearer {{token}}`. El
> token dura 8 horas; si caduca, vuelve a ejecutar el login. Sin él devuelven
> `UNAUTHORIZED`. (*Mutación: cancelar* no lo necesita: envía `"por":
> "cliente"`, que es pública.) La carpeta **00 Flujo completo** y las demás
> (REST directo a los servicios) no cambian.

> **Confirmación en la carpeta 06.** Dos peticiones nuevas: *Mutación: confirmar
> (pública, sin Authorization)* y *Agenda con confirmadaEn*. La primera no lleva
> token y usa el `citaGqlId` que guarda *Mutación: reservar*, así que se ejecuta
> **después** de reservar y **antes** de cerrar o cancelar. Devuelve la cita con
> `confirmadaEn` lleno y `estado` todavía `agendada`.

La carpeta **06 Gateway GraphQL** es la que conviene enseñar si preguntan por
GraphQL. Tiene dos piezas clave:

- *Agenda enriquecida*: una sola consulta que el gateway resuelve llamando a
  dos microservicios y devolviendo todo junto.
- *Error a propósito*: pide un campo que no existe en el esquema, y el gateway
  lo rechaza sin llamar a ningún microservicio. Eso demuestra que el esquema
  **es** el contrato.

---

## Si algo falla

Primero, mira de qué servicio es el error. Luego saca sus logs:

```bash
docker compose logs --tail=80 gateway-graphql
```

Cambia el nombre por el servicio que falle: `ms-agenda`, `ms-operaciones`,
`ms-establecimientos`, `ms-relacion-cliente`, `kafka`, `postgres`.

Errores conocidos y qué significan:

| Lo que ves | Qué es |
|---|---|
| `container name is already in use` | Tienes una copia vieja levantada. Mira la Parte 1 |
| `advertised.listeners cannot use 0.0.0.0` | Config de Kafka mal escrita. Ya está corregida en este compose |
| `relation "citas" does not exist` | Postgres no corrió el script de esquema porque el volumen traía datos viejos. `docker compose down -v` y vuelve a subir |
| `INTERNAL_ERROR` en el frontend | Falló algo dentro del gateway. GraphQL oculta el detalle al cliente a propósito. El detalle está en los logs del gateway |
| `Inicia sesion para continuar` / `La sesion vencio…` (`UNAUTHORIZED`) | Una operación del panel del establecimiento llegó sin token o con uno vencido o alterado. En el frontend lleva solo al login. En GraphiQL o Postman hay que mandar `Authorization: Bearer <token>` |
| `No tienes acceso a ese establecimiento` (`FORBIDDEN`) | El token es válido, pero de otro establecimiento que el consultado |
| El login dice «Correo o clave incorrectos» con `admin@demo.com` / `demo1234` | Muy probable que el usuario de prueba aún no exista: espera ~10 s tras arrancar. Compruébalo: `docker exec agenda-postgres psql -U citas -d citas -c "select correo from establecimientos.usuario"` |
| `INTERNAL_ERROR` al confirmar o anular una cita | La cita no existe o ya no está agendada (el servicio responde 404/409 y el gateway lo muestra como error interno). Limitación conocida, ver más abajo |
| El panel de http://localhost:8000 muestra «Error al consultar: Inicia sesion para continuar» | Es esperado hoy: ese panel estático no envía token. Mira *Limitaciones del inicio de sesión* |

---

## Limitaciones declaradas

Estas son conocidas y están documentadas. Es mejor que las digas tú antes de
que las encuentre el profesor.

1. **No hay autenticación ni registro de clientes.** Por eso el frontend genera
   un identificador para cada clienta. Está declarado como pendiente. Lo que sí
   tiene inicio de sesión es el panel del establecimiento; sus límites están
   en la sección siguiente.
2. **Cancelar una cita no libera el cupo.** La restricción `UNIQUE` no filtra
   por estado, así que reservar, cancelar y volver a reservar la misma hora
   sigue devolviendo `409`. Se arregla cambiando la restricción a un índice
   parcial que ignore las canceladas.
3. **La duración del servicio no recorta la franja.** La jornada son franjas
   fijas de una hora, aunque el servicio dure dos.
4. **El canal de notificación es simulado.** Se registran en base de datos, no
   se envían por WhatsApp ni correo.

---

## Limitaciones del inicio de sesión

El login protege el panel del establecimiento, pero tiene límites que conviene
decir en voz alta antes de que los encuentren:

1. **`agenda` es pública.** La consulta `agenda` la usa también *Mis citas* de
   la versión cliente, así que no se puede proteger sin romperla. Quien conozca
   un `profesionalId` puede leer las citas de esa profesional, con el
   `clienteId` de cada una.
2. **`cerrarCita` solo exige sesión.** No recibe el establecimiento, así que
   cualquier usuario con sesión puede cerrar cualquier cita, también la de otro
   establecimiento. Las consultas `inventario`, `resumenIngresos`,
   `notificaciones` y `clientesInactivos` sí comprueban que el establecimiento
   consultado sea el del token (si no, `FORBIDDEN`).
3. **`canceladaPor` lo envía el cliente.** `cancelarCita` es pública cuando
   `canceladaPor` es `"cliente"` y exige sesión con cualquier otro valor. Pero
   ese texto lo escribe quien hace la petición: cualquiera puede mandar
   `"cliente"` y cancelar sin sesión. No empeora lo de antes (esa mutación era
   abierta), pero tampoco lo cierra.
4. **Los puertos REST 8080–8083 siguen publicados.** `docker-compose.yml`
   los expone en tu equipo, y esas APIs no piden token: quien llegue a ellos se
   salta el gateway y el login. Cerrarlos exige modificar el compose, y
   `demo.sh` y Postman los usan, por eso no se tocó.
5. **No hay límite de intentos de login.** Se puede probar claves sin freno.
6. **El token vive en `localStorage`.** Lo puede leer cualquier script que
   corra en la página (XSS). Se eligió así para que funcionen las pestañas
   nuevas y el cierre de sesión entre pestañas; una cookie `httpOnly`
   obligaría a manejar CSRF y CORS con credenciales.
7. **El secreto de desarrollo está en el repositorio.** Cualquiera que lo
   conozca puede fabricar tokens válidos. Hay que definir `JWT_SECRET` fuera de
   desarrollo (ver *Credenciales*).
8. **Solo existe el usuario de prueba.** No hay pantalla ni endpoint para
   crear usuarios, cambiar la clave o recuperarla. El de prueba debe apagarse
   fuera de desarrollo (`SEMILLA_USUARIO_ACTIVA: "false"`).
9. **El panel estático de http://localhost:8000 dejó de mostrar datos
   protegidos.** Muestra «Error al consultar: Inicia sesion para continuar» y
   los indicadores en «—», porque pide ingresos, inventario y notificaciones
   sin token y no tiene forma de iniciar sesión. Se dejó así a propósito, sin
   modificarlo. El panel React (http://localhost:3000/negocio) es el que
   funciona con sesión.
10. **Un error de autenticación en una lista deja `data` en `null`.** En
    `inventario`, `notificaciones` y `clientesInactivos` el esquema declara la
    lista como no nula, y GraphQL propaga el error hasta anular toda la
    respuesta (en `resumenIngresos`, que admite nulo, solo se anula ese campo).
    No afecta a la pantalla con sesión vigente, porque al vencer se cierra la
    sesión y vuelve al login.

En GraphiQL (http://localhost:8000/graphiql), para consultar lo protegido: haz
primero la mutación `login`, copia el `token` y pégalo en la pestaña *Headers*
como `{"Authorization": "Bearer <token>"}`.

---

## Limitaciones del recordatorio y la confirmación

1. **Cualquiera con el `citaId` puede confirmar.** `confirmarCita` es pública,
   igual que reservar y anular como cliente: no hay registro de clientas.
2. **404 y 409 salen como `INTERNAL_ERROR`.** Confirmar (o anular) una cita que
   no existe, o que ya no está agendada, devuelve ese error genérico en
   GraphQL. La pantalla muestra «No pudimos confirmar tu cita. Inténtalo de
   nuevo.». El detalle real está en los logs de `ms-agenda`.
3. **Pico inicial de recordatorios.** En la primera pasada del job, las citas
   que ya están dentro de la ventana (las del simulador, por ejemplo) reciben su
   recordatorio de golpe (una docena en la prueba). Después son unos pocos por
   hora.
4. **La ventana solo decide cuándo recordar.** No libera cupos ni anula citas
   sin confirmar.
5. **El recordatorio no se envía.** Queda registrado en la base y se ve en
   *Clientas*; no hay WhatsApp ni correo.
6. **Cambio en `docker-compose.yml`.** Solo dos líneas en `ms-relacion-cliente`
   (`AGENDA_URL` y `ESTABLECIMIENTOS_URL`): el job necesita llamar a
   `ms-agenda` y `ms-establecimientos`, y dentro de un contenedor `localhost`
   es el propio contenedor.
