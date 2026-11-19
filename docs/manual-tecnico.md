# Manual técnico · Plataforma de citas (AGENDAPP)

Escrito a partir del código y de `docker-compose.yml` del repositorio. Lo que no se pudo comprobar en el código figura como **no verificado**. Las rutas y el esquema GraphQL están en [referencia-api.md](referencia-api.md); las pruebas, en [pruebas-y-evidencias.md](pruebas-y-evidencias.md).

## 1. Arquitectura

Diez contenedores definidos en `docker-compose.yml` (los diagramas están en [docs/diagramas/](diagramas/)).

| # | Servicio (contenedor) | Imagen / build | Puerto en el equipo → contenedor | Depende de (`depends_on`) |
|---|---|---|---|---|
| 1 | `postgres` (`agenda-postgres`) | `postgres:16` | 5432 → 5432 | — |
| 2 | `kafka` (`agenda-kafka`) | `apache/kafka:3.9.0` (KRaft, un nodo) | 9092 → 9092 | — |
| 3 | `kafka-ui` (`agenda-kafka-ui`) | `provectuslabs/kafka-ui:latest` | 8090 → 8080 | kafka (healthy) |
| 4 | `ms-establecimientos` | build `./ms-establecimientos` | 8081 → 8081 | postgres (healthy) |
| 5 | `ms-agenda` | build `./ms-agenda` | 8080 → 8080 | postgres (healthy), kafka (healthy) |
| 6 | `ms-operaciones` | build `./ms-operaciones` | 8082 → 8082 | postgres (healthy), kafka (healthy) |
| 7 | `ms-relacion-cliente` | build `./ms-relacion-cliente` | 8083 → 8083 | postgres (healthy), kafka (healthy) |
| 8 | `simulador` | build `./simulador` | 8084 → 8084 | ms-establecimientos, ms-agenda, ms-operaciones (solo arranque, sin condición) |
| 9 | `gateway-graphql` | build `./gateway-graphql` | 8000 → 8000 | los cuatro `ms-*` (solo arranque, sin condición) |
| 10 | `frontend` | build `./frontend` (nginx) | 3000 → 3000 | gateway-graphql |

Notas verificadas en el compose y el código:

- Un solo volumen con nombre: `agenda-data` (datos de Postgres). Kafka usa `/tmp/kraft-combined-logs` sin volumen, de modo que **los mensajes de Kafka no sobreviven a recrear el contenedor `kafka`** (el compose no declara volumen para él).
- El compose declara healthcheck solo para `postgres` y `kafka`. Los demás servicios no tienen healthcheck.
- `kafka-ui` es una herramienta de desarrollo; el propio compose dice que no forma parte de la arquitectura entregada.
- El `frontend` solo habla con el gateway: `nginx.conf` reenvía `/graphql` a `http://gateway-graphql:8000/graphql`. El navegador no llama a las APIs REST.
- Kafka anuncia dos listeners: `kafka:29092` (entre contenedores) y `localhost:9092` (desde el equipo). `KAFKA_AUTO_CREATE_TOPICS_ENABLE` está en `"true"`: los topics no se declaran en ningún archivo; se crean al primer uso.
- Comunicación entre servicios:
  - REST síncrono: gateway → los cuatro `ms-*`; ms-operaciones → ms-establecimientos (`GET /servicios/{id}/insumos`); ms-relacion-cliente → ms-agenda (`GET /citas/proximas`) y → ms-establecimientos (`GET /establecimientos`); simulador → ms-establecimientos, ms-agenda y ms-operaciones.
  - Kafka asíncrono: ver sección 3.
- Orden de arranque: `depends_on` sin `condition` solo ordena el inicio de los contenedores, no garantiza que el servicio esté listo. Los servicios que dependen unos de otros toleran esa espera mediante retardos iniciales (por ejemplo `SemillaDatos` del simulador y el retardo de 30 s del job de recordatorios).

## 2. Stack y versiones

| Componente | Versión | Dónde se comprobó |
|---|---|---|
| Java | 21 | `<java.version>21</java.version>` en todos los `pom.xml`; imágenes `maven:3.9-eclipse-temurin-21` y `eclipse-temurin:21-jre` en los Dockerfile |
| Spring Boot | 3.3.4 | `spring-boot-starter-parent` en los seis `pom.xml` |
| springdoc-openapi | 2.6.0 | `pom.xml` de ms-agenda, ms-establecimientos, ms-operaciones, ms-relacion-cliente |
| jjwt | 0.12.6 | `pom.xml` del gateway |
| React | 18.3.1 (rango `^18.3.1`) | `frontend/package.json`; versión resuelta en `package-lock.json` |
| react-router-dom | 6.30.6 (rango `^6.26.2`) | `package-lock.json` |
| Vite | 5.4.21 (rango `^5.4.8`) | `package-lock.json` |
| Node | 20 | `FROM node:20-alpine` en `frontend/Dockerfile` (solo para compilar) |
| nginx | `nginx:alpine` (sin versión fija) | `frontend/Dockerfile` |
| Kafka | 3.9.0 | `image: apache/kafka:3.9.0` |
| PostgreSQL | 16 | `image: postgres:16` |
| kafka-ui | `latest` (sin versión fija) | compose |
| Maven (build en imagen) | 3.9 | `maven:3.9-eclipse-temurin-21` |

Dependencias por servicio (según `pom.xml`): los cuatro `ms-*` usan Spring Web, Validation (excepto el gateway), Data JPA y el driver PostgreSQL; ms-agenda, ms-operaciones y ms-relacion-cliente añaden `spring-kafka`; ms-establecimientos añade `spring-security-crypto` (BCrypt, sin Spring Security completo); el gateway usa `spring-boot-starter-graphql` y jjwt; el simulador solo Spring Web. Para pruebas: H2 en ms-agenda y ms-operaciones, `spring-kafka-test` y Awaitility.

Versión de `spring-kafka` y del cliente Kafka que trae Spring Boot 3.3.4: **no verificado** (no se fijan en los `pom.xml`; las resuelve el BOM de Spring Boot).

## 3. Kafka: tópicos y grupos

Productores y consumidores, tal como están en el código:

| Tópico | Lo publica | Clave de partición | Lo consume (grupo) |
|---|---|---|---|
| `citas.agendada` | ms-agenda (`PublicadorKafka`, tras `AFTER_COMMIT`) | `establecimientoId` | ms-relacion-cliente (`relacion-cliente`) |
| `citas.cancelada` | ms-agenda | `establecimientoId` | ms-relacion-cliente (`relacion-cliente`) |
| `citas.completada` | ms-agenda | `establecimientoId` | ms-operaciones (`operaciones`) y ms-relacion-cliente (`relacion-cliente`) |
| `inventario.insumo-bajo` | ms-operaciones (`OperacionesService`) | `establecimientoId` | ms-relacion-cliente (`relacion-cliente`) |
| `<tópico>.DLT` | `DeadLetterPublishingRecoverer` de ms-operaciones y ms-relacion-cliente | (conserva la partición original) | nadie (no hay consumidor de DLT en el código) |

- Grupos de consumo: `operaciones` (`ms-operaciones/application.properties`) y `relacion-cliente` (`ms-relacion-cliente/application.properties`). `auto-offset-reset=earliest` en ambos.
- Los eventos viajan como JSON en texto (serializadores `String`). Los tres tópicos de citas comparten la forma `EventoCita`: `citaId, establecimientoId, profesionalId, servicioId, clienteId, inicio, fin, estado, valorCobrado, motivo, ocurridoEn`.
- `confirmarCita` **no publica ningún evento** (lo indica el comentario de `CitaService.confirmar`).
- Manejo de errores del consumidor (`ManejoDeErroresKafka`, en ms-operaciones y ms-relacion-cliente): 3 reintentos con 2 s de espera; si siguen fallando, el mensaje va a `<tópico>.DLT`. `SerializationException` e `IllegalArgumentException` (JSON malformado) no se reintentan y van directo al DLT.
- Idempotencia: `UNIQUE(cita_id)` en `operaciones.liquidacion` y `UNIQUE(cita_id, tipo)` en `relacion_cliente.notificacion`.
- Número de particiones y factor de replicación de cada tópico: **no verificado**; no se configuran en el repositorio (creación automática con los valores por defecto del broker).
- Si ms-agenda no logra publicar, solo registra el error en el log: la cita ya quedó guardada y el evento **no se reintenta** (no hay patrón outbox). Ver limitaciones.

## 4. Base de datos

Una instancia PostgreSQL 16 (`citas`, usuario `citas`) con **cuatro esquemas**, uno por servicio: `establecimientos`, `agenda`, `operaciones`, `relacion_cliente`.

**Cómo se crean:**

- `db-init/01-esquemas.sql` **solo crea los esquemas** (`CREATE SCHEMA IF NOT EXISTS …`). Se ejecuta únicamente la primera vez que se inicializa el volumen de Postgres (carpeta `docker-entrypoint-initdb.d`).
- **Las tablas no salen de ningún script SQL.** Las crea Hibernate: cada servicio tiene `spring.jpa.hibernate.ddl-auto=update` y `default_schema=<su esquema>`. No hay Flyway ni Liquibase (no aparecen en los `pom.xml`).
- Además, cada servicio con base de datos tiene `hibernate.hbm2ddl.create_namespaces=true`, así que crea su esquema si no existe aunque el volumen sea anterior al script.
- Comentarios desactualizados: `ms-agenda/application.properties` menciona `db-init/01-schema.sql`; el archivo real se llama `01-esquemas.sql`.

**Tablas** (según las anotaciones `@Table` de las entidades):

| Esquema | Tabla | Restricciones únicas declaradas |
|---|---|---|
| `establecimientos` | `establecimiento`, `profesional`, `servicio`, `servicio_insumo`, `usuario` | `usuario.correo` único |
| `agenda` | `cita` | `cupo_unico (profesional_id, inicio)` |
| `operaciones` | `insumo`, `liquidacion`, `movimiento_insumo` | `liquidacion_unica_por_cita (cita_id)` |
| `relacion_cliente` | `cliente_recurrencia`, `notificacion` | `cliente_unico_por_establecimiento (establecimiento_id, cliente_id)`; `notificacion_unica (cita_id, tipo)` |

Ningún servicio consulta el esquema de otro; los datos cruzados viajan por REST o por evento.

**Dónde se siembra el usuario admin** (verificado en el código): en `ms-establecimientos/…/SembradoUsuarios.java`, **no** en `db-init`. Es una tarea `@Scheduled(initialDelay = 5000, fixedDelay = 10000)` que, si `usuarios.count() == 0`, crea un usuario (rol `ADMIN`, nombre «Administrador», clave guardada con BCrypt) ligado al establecimiento **más antiguo** por `creadoEn`. Como los establecimientos los crea el simulador unos segundos después del arranque, la tarea reintenta cada 10 s hasta que existe uno. Credenciales por defecto: `admin@demo.com` / `demo1234`. Se desactiva con `SEMILLA_USUARIO_ACTIVA=false`.

Consecuencia: si el simulador está apagado y nadie crea un establecimiento, **no se crea ningún usuario** y el login no funciona hasta que exista uno.

Los datos de demostración (establecimiento, profesional, servicio, insumo y receta) los crea `SemillaDatos` del simulador llamando a las APIs REST.

## 5. Variables de entorno por servicio

Fuente: `docker-compose.yml` (columna «Compose») y `application.properties` (valor por defecto). «Compose» indica si el compose **pasa** la variable.

| Servicio | Variable | Compose | Valor por defecto en el código |
|---|---|---|---|
| ms-establecimientos | `DB_URL`, `DB_USER`, `DB_PASSWORD` | sí (`jdbc:postgresql://postgres:5432/citas`, `citas`, `citas`) | `jdbc:postgresql://localhost:5432/citas`, `citas`, `citas` |
| ms-establecimientos | `SEMILLA_USUARIO_ACTIVA`, `SEMILLA_USUARIO_CORREO`, `SEMILLA_USUARIO_CLAVE` | **no** | `true`, `admin@demo.com`, `demo1234` |
| ms-agenda | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_SERVERS` | sí (`kafka:29092`) | `localhost:9092` para Kafka |
| ms-operaciones | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_SERVERS`, `ESTABLECIMIENTOS_URL` | sí | `http://localhost:8081` |
| ms-relacion-cliente | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_SERVERS`, `AGENDA_URL`, `ESTABLECIMIENTOS_URL` | sí | `http://localhost:8080`, `http://localhost:8081` |
| ms-relacion-cliente | `RECORDATORIOS_ACTIVO`, `RECORDATORIOS_CADA_MS`, `RECORDATORIOS_ESPERA_INICIAL_MS` | **no** | `true`, `60000`, `30000` |
| simulador | `ESTABLECIMIENTOS_URL`, `AGENDA_URL`, `OPERACIONES_URL`, `SIMULADOR_ACTIVO`, `SIMULADOR_INTERVALO_MS` | sí (`"true"` y `"15000"`) | `localhost:8081/8080/8082`; `SIMULADOR_ACTIVO=false`; `15000` |
| gateway-graphql | `ESTABLECIMIENTOS_URL`, `AGENDA_URL`, `OPERACIONES_URL`, `RELACION_URL` | sí | `localhost:8081/8080/8082/8083` |
| gateway-graphql | `JWT_SECRET`, `JWT_MINUTOS` | **no** | `clave-de-desarrollo-cambiar-en-produccion-0123456789`, `480` |
| frontend | ninguna | — | `nginx.conf` fija el destino `gateway-graphql:8000` |
| postgres | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | sí (`citas`, `citas`, `citas`) | — |
| kafka | variables `KAFKA_*` y `CLUSTER_ID` | sí | — |

Valores fijos en el código que **no** son variables de entorno: `app.simulador.espera-inicial-ms=30000`, `app.recurrencia.dias=28`, `server.port` de cada servicio (ms-agenda no lo fija y usa el 8080 por defecto de Spring).

`.env.example` es solo documental: el compose no usa `${VARIABLE}`, así que copiarlo a `.env` no cambia nada.

### Qué no pasa el compose y cómo se cambiaría

El compose **NO pasa** `JWT_SECRET`, `JWT_MINUTOS` ni `RECORDATORIOS_*` (tampoco `SEMILLA_USUARIO_*`). Mientras tanto aplican los valores por defecto de la tabla anterior; en particular, **el secreto JWT es el valor de desarrollo, que está escrito en el repositorio**.

Para cambiarlos sin editar `docker-compose.yml`, crear junto a él un `docker-compose.override.yml` (Compose lo carga solo; el `.gitignore` del repositorio ya lo ignora, según `.env.example`):

```yaml
services:
  gateway-graphql:
    environment:
      JWT_SECRET: "una-clave-de-al-menos-32-caracteres"
      JWT_MINUTOS: "60"
  ms-relacion-cliente:
    environment:
      RECORDATORIOS_ACTIVO: "false"      # o RECORDATORIOS_CADA_MS: "30000"
```

y aplicarlo con `docker compose up -d --no-deps gateway-graphql ms-relacion-cliente` (no recrea el resto ni toca el volumen). La otra vía es añadir esas líneas al bloque `environment` del servicio en `docker-compose.yml`. No se ejecutó este procedimiento durante la redacción: **no verificado en ejecución**.

## 6. Seguridad

### JWT (lo emite y valida solo el gateway)

- Algoritmo HS256, librería jjwt 0.12.6, clase `EmisorJwt`.
- **Claims:** `sub` = id del usuario, `est` = id del establecimiento, `nom` = nombre, `rol`, `iat` y `exp`.
- **Expiración:** `app.jwt.minutos` = `JWT_MINUTOS`, por defecto **480 minutos (8 h)**; debe ser mayor que 0 o el gateway no arranca.
- **Secreto:** `JWT_SECRET`, mínimo 32 bytes; con menos, el gateway no arranca (`IllegalArgumentException`).
- Flujo de login: la mutación `login` llama a `POST /auth/verificar` de ms-establecimientos (que compara con BCrypt y no emite token); el gateway fabrica el token. Credenciales erróneas, usuario inactivo o correo inexistente dan el mismo mensaje: «Correo o clave incorrectos».
- `InterceptorSesion` lee `Authorization: Bearer <token>`; **nunca rechaza la petición**: deja la sesión (o el motivo `vencida`/`invalida`) en el contexto, y cada operación decide si exige sesión (`Exigir`).
- Errores: sin sesión → `UNAUTHORIZED`; sesión de otro establecimiento → `FORBIDDEN` (en `extensions.classification`).

### Operaciones GraphQL públicas y protegidas

| Pública (sin token) | Protegida (exige sesión) |
|---|---|
| `establecimientos`, `profesionales`, `servicios`, `agenda`, `disponibilidad` | `inventario`, `resumenIngresos`, `notificaciones`, `clientesInactivos` (sesión **del mismo** `establecimientoId`; otro → `FORBIDDEN`) |
| `reservarCita`, `confirmarCita`, `login` | `cerrarCita` (cualquier sesión válida) |
| `cancelarCita` cuando `canceladaPor` es `"cliente"` | `cancelarCita` con cualquier otro valor de `canceladaPor` (cualquier sesión válida) |

### Lo que NO está protegido

- **Las APIs REST de los microservicios no tienen autenticación** (no hay Spring Security en ningún `ms-*`) y sus puertos están publicados en el equipo (8080–8083). El control de sesión existe solo en el gateway; quien llame a un microservicio directamente lo evita.
- `cerrarCita` y `cancelarCita` (no cliente) exigen sesión, pero **no comprueban que la cita pertenezca al establecimiento de la sesión**.
- `agenda` es pública y devuelve el `clienteId` de cada cita.
- CORS del gateway permite `localhost:3000`, `5173`, `4200` y `8000`.

## 7. Job de recordatorios

`GeneradorRecordatorios` (ms-relacion-cliente), con `@Scheduled(fixedDelay = RECORDATORIOS_CADA_MS, initialDelay = RECORDATORIOS_ESPERA_INICIAL_MS)`: por defecto una revisión **cada 60 s, con 30 s de espera inicial**.

Cada vuelta:

1. Pide a ms-establecimientos la lista de establecimientos y su `ventanaConfirmacionHoras` (24 h por defecto al crear, `EstablecimientoController`). Si no hay establecimientos o no responde, registra un aviso y reintenta en la siguiente vuelta.
2. Pide a ms-agenda `GET /citas/proximas` entre ahora y ahora + la ventana más ancha, recorriendo todas las páginas.
3. Para cada cita agendada **no confirmada** cuyo inicio cumple `ahora < inicio <= ahora + ventana de su establecimiento`, registra una notificación `recordatorio-cita` (canal `simulado`, estado `registrada`, fijados en la entidad `Notificacion`; el texto usa la hora de Colombia, UTC-5).
4. La idempotencia la da la base: `UNIQUE(cita_id, tipo)`. Repetir la vuelta, reiniciar el servicio o correr dos copias no duplica.

La ventana solo decide **cuándo recordar**: no libera cupos ni anula citas.

### Decisión: activo por defecto

`app.recordatorios.activo=${RECORDATORIOS_ACTIVO:true}`: el job está **encendido salvo que se ponga `RECORDATORIOS_ACTIVO=false`**. El bean usa `@ConditionalOnProperty(… havingValue = "true")`, así que con `false` el bean no existe. El README lo declara («Está activo por defecto») pero **no escribe una justificación**. Lo que sí se puede comprobar es la consecuencia: como el compose no pasa `RECORDATORIOS_*`, el valor que manda es el del `application.properties`, así que con `true` los recordatorios funcionan con `docker compose up` sin editar nada, y apagarlo exige un override (sección 5). Que ese haya sido el motivo de la decisión es una inferencia, **no verificado**.

Nota: el comentario de Javadoc de `GeneradorRecordatorios` dice «Por defecto esta apagado», pero la propiedad dice lo contrario (`true`) y es la que manda. Es un comentario desactualizado.

## 8. Única modificación a `docker-compose.yml`

Según `README.md` (sección del job de recordatorios) y `COMO_PROBAR.md`, la única modificación al compose fueron **dos líneas en el servicio `ms-relacion-cliente`**:

```yaml
      AGENDA_URL: http://ms-agenda:8080
      ESTABLECIMIENTOS_URL: http://ms-establecimientos:8081
```

Son necesarias porque el job llama por REST a otros contenedores y, dentro de un contenedor, `localhost` es él mismo; antes ese servicio no recibía ninguna dirección de otro servicio. Sigue el mismo patrón que `ms-operaciones` con `ESTABLECIMIENTOS_URL`. No cambia `depends_on` ni nada más.

Qué está comprobado y qué no: ambas líneas **existen** hoy en el compose, en `ms-relacion-cliente`. Que sean la *única* modificación **no se puede verificar con git**: el historial tiene un solo commit que toca el archivo (`2a29f57`, «Versión inicial»), que ya las incluye; la afirmación de que es la única modificación proviene de la documentación del repositorio.

## 9. Limitaciones conocidas

Comprobadas en el código salvo que se indique otra cosa.

- **Sin autenticación en los microservicios** (sección 6) y secreto JWT por defecto público en el repositorio.
- **Sin outbox:** si Kafka no responde al publicar (`max.block.ms=5000`), ms-agenda guarda la cita, registra el error y el evento se pierde.
- **Sin consumidor de DLT:** los mensajes que llegan a `.DLT` quedan almacenados, pero ningún servicio los reprocesa.
- **Disponibilidad simplificada:** jornada fija 09:00–18:00 en franjas de una hora, zona fija UTC-5; el `servicioId` de la consulta `disponibilidad` no se usa (la duración del servicio no recorta la franja).
- **Una cita cancelada sigue ocupando el cupo:** ni `existsByProfesionalIdAndInicio` ni la restricción `cupo_unico (profesional_id, inicio)` distinguen el estado, así que volver a reservar la misma hora de un profesional tras cancelar daría `409`, y `disponibilidad` seguiría marcando esa franja como ocupada. Contradice el texto «libera el cupo» de la API. *Deducido del código; no probado en ejecución (no hay test que lo cubra).*
- **Reservas:** `ms-agenda` no valida que `fin` sea posterior a `inicio`, ni que profesional, servicio o establecimiento existan (los DTO solo exigen campos no nulos). El único control de conflicto es `UNIQUE(profesional_id, inicio)`: dos citas solapadas con distinta hora de inicio no se detectan. *(Deducido de `ReservarCitaRequest` y `CitaService.reservar`; no probado.)*
- **Errores REST → GraphQL:** el gateway no traduce 404/409 y salen como `INTERNAL_ERROR` (lo documenta el README; no reproducido en esta redacción).
- **Notificaciones simuladas:** no se envía nada a un canal real (WhatsApp, correo); las notificaciones solo se guardan (comentario en la entidad `Notificacion`).
- **Fechas como `String`** en GraphQL.
- **Sin paginación** en `GET /establecimientos`, `/servicios`, `/inventario`, `/notificaciones`, `/liquidaciones` y `/clientes-inactivos`.
- **Los tópicos no sobreviven a recrear `kafka`** (sin volumen) ni se declaran: se autocrean.
- **Un solo nodo** de Kafka y de Postgres; sin alta disponibilidad.
- **Imágenes sin versión fija:** `kafka-ui:latest` y `nginx:alpine`.
- **Pruebas:** ms-establecimientos, simulador y frontend no tienen tests (ver [pruebas-y-evidencias.md](pruebas-y-evidencias.md)).
- **El usuario admin depende del simulador** para existir (sección 4).
