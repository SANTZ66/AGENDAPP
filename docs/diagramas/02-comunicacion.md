# 2. Comunicación REST y Kafka

## 2a. Llamadas REST entre servicios

```mermaid
flowchart LR
  subgraph g["gateway-graphql llama a"]
    g0["gateway-graphql"] --> g1["ms-establecimientos"]
    g0 --> g2["ms-agenda"]
    g0 --> g3["ms-operaciones"]
    g0 --> g4["ms-relacion-cliente"]
  end
  subgraph s["simulador llama a"]
    s0["simulador"] --> s1["ms-establecimientos"]
    s0 --> s2["ms-agenda"]
    s0 --> s3["ms-operaciones"]
  end
  subgraph o["ms-operaciones llama a"]
    o0["ms-operaciones"] -->|"GET /servicios/{id}/insumos"| o1["ms-establecimientos"]
  end
  subgraph r["ms-relacion-cliente llama a"]
    r0["ms-relacion-cliente"] -->|"GET /citas/proximas"| r1["ms-agenda"]
    r0 -->|"GET /establecimientos"| r2["ms-establecimientos"]
  end
```

![REST](img/02a-comunicacion-rest.png)

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
