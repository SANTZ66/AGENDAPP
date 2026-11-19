# 2. Comunicación REST y Kafka

## 2a. Llamadas REST entre servicios

```mermaid
flowchart LR
  gw["gateway-graphql"]
  sim["simulador"]
  op["ms-operaciones"]
  rc["ms-relacion-cliente"]
  est["ms-establecimientos"]
  ag["ms-agenda"]
  opd["ms-operaciones"]
  rcd["ms-relacion-cliente"]
  gw -->|"GET /establecimientos<br/>GET /establecimientos/{id}/profesionales<br/>GET /servicios, GET /servicios/{id}<br/>POST /auth/verificar"| est
  gw -->|"GET /citas, GET /disponibilidad<br/>POST /citas<br/>POST /citas/{id}/confirmacion<br/>POST /citas/{id}/cierre<br/>POST /citas/{id}/cancelacion"| ag
  gw -->|"GET /inventario<br/>GET /resumen"| opd
  gw -->|"GET /notificaciones<br/>GET /clientes-inactivos"| rcd
  sim -->|"POST /establecimientos<br/>POST /establecimientos/{id}/profesionales<br/>POST /servicios<br/>POST /servicios/{id}/insumos"| est
  sim -->|"POST /citas<br/>POST /citas/{id}/cierre<br/>POST /citas/{id}/cancelacion"| ag
  sim -->|"POST /inventario"| opd
  op -->|"GET /servicios/{id}/insumos"| est
  rc -->|"GET /establecimientos"| est
  rc -->|"GET /citas/proximas"| ag
```

![REST](img/02a-comunicacion-rest.png)

Detalle de la llamada REST, una fila por origen, destino y endpoint:

| Origen | Destino | Endpoint | Fuente |
|---|---|---|---|
| gateway-graphql | ms-establecimientos | GET /establecimientos; GET /establecimientos/{id}/profesionales; GET /servicios; GET /servicios/{id}; POST /auth/verificar | `ConsultasController.java`, `AutenticacionController.java` |
| gateway-graphql | ms-agenda | GET /citas; GET /disponibilidad; POST /citas; POST /citas/{id}/confirmacion; POST /citas/{id}/cierre; POST /citas/{id}/cancelacion | `ConsultasController.java` |
| gateway-graphql | ms-operaciones | GET /inventario; GET /resumen | `ConsultasController.java` |
| gateway-graphql | ms-relacion-cliente | GET /notificaciones; GET /clientes-inactivos | `ConsultasController.java` |
| simulador | ms-establecimientos | POST /establecimientos; POST /establecimientos/{id}/profesionales; POST /servicios; POST /servicios/{id}/insumos | `SemillaDatos.java` |
| simulador | ms-agenda | POST /citas; POST /citas/{id}/cierre; POST /citas/{id}/cancelacion | `GeneradorDeCitas.java` |
| simulador | ms-operaciones | POST /inventario | `SemillaDatos.java` |
| ms-operaciones | ms-establecimientos | GET /servicios/{id}/insumos | `CatalogoClient.java` |
| ms-relacion-cliente | ms-establecimientos | GET /establecimientos | `EstablecimientosClient.java` |
| ms-relacion-cliente | ms-agenda | GET /citas/proximas | `AgendaClient.java` |

## 2b. Topics de Kafka

```mermaid
flowchart TB
  ag["ms-agenda (productor)"]
  op["ms-operaciones"]
  rc["ms-relacion-cliente"]
  t1(["citas.agendada"])
  t2(["citas.completada"])
  t3(["citas.cancelada"])
  t4(["inventario.insumo-bajo"])
  ag ==>|produce| t1
  ag ==>|produce| t2
  ag ==>|produce| t3
  op ==>|produce| t4
  t2 -->|"grupo: operaciones"| op
  t1 -->|"grupo: relacion-cliente"| rc
  t2 -->|"grupo: relacion-cliente"| rc
  t3 -->|"grupo: relacion-cliente"| rc
  t4 -->|"grupo: relacion-cliente"| rc
  op -.->|"tras 3 reintentos"| d1(["citas.completada.DLT"])
  rc -.->|"tras 3 reintentos"| d1
  rc -.->|"tras 3 reintentos"| d2(["citas.agendada.DLT<br/>citas.cancelada.DLT<br/>inventario.insumo-bajo.DLT"])
```

![Kafka](img/02b-comunicacion-kafka.png)

## Verificado contra

| Elemento | Dónde se comprobó |
|---|---|
| Gateway -> 4 servicios | `ServiciosInternos.java` (`establecimientos`, `agenda`, `operaciones`, `relacion`) y uso en `ConsultasController.java` |
| Simulador -> establecimientos, agenda, operaciones | `ApiClient.java`; `GeneradorDeCitas.java` (agenda), `SemillaDatos.java` (establecimientos, operaciones `/inventario`) |
| ms-operaciones -> `GET /servicios/{id}/insumos` en ms-establecimientos | `CatalogoClient.java` |
| ms-relacion-cliente -> `GET /citas/proximas` (ms-agenda) y `GET /establecimientos` | `AgendaClient.java`, `EstablecimientosClient.java` |
| ms-agenda produce `citas.agendada`, `citas.completada`, `citas.cancelada` | `HechoDeCita.java`, `PublicadorKafka.java` (AFTER_COMMIT) |
| ms-operaciones produce `inventario.insumo-bajo` | `OperacionesService.TOPIC_INSUMO_BAJO` |
| Grupo `operaciones` consume `citas.completada` | `EscuchaCitas.java` |
| Grupo `relacion-cliente` consume los 4 topics | `EscuchaEventos.java` |
| `.DLT`: 3 reintentos cada 2 s y luego `<topic>.DLT` (`DeadLetterPublishingRecoverer`) | `ManejoDeErroresKafka.java` en ms-operaciones y ms-relacion-cliente |

Nota sobre `.DLT`: ambos consumidores usan el nombre por defecto, por lo que un fallo de `citas.completada` en cualquiera de los dos grupos termina en el mismo `citas.completada.DLT`. ms-agenda y ms-establecimientos no consumen eventos. Los topics `.DLT` solo existen si ocurrió un fallo.
