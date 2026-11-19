# 1. Arquitectura y despliegue

AGENDAPP - Equipo 5, Grupo 3, Programación Avanzada, FET. Fuente: `docker-compose.yml`, `frontend/nginx.conf`, `db-init/01-esquemas.sql`.

```mermaid
flowchart LR
  nav["Navegador"]
  subgraph dc["Docker Compose - volumen agenda-data"]
    direction LR
    fe["frontend<br/>nginx :3000"]
    gw["gateway-graphql<br/>:8000"]
    sim["simulador<br/>:8084"]
    subgraph java["Microservicios Java"]
      direction TB
      est["ms-establecimientos<br/>:8081"]
      ag["ms-agenda<br/>:8080"]
      op["ms-operaciones<br/>:8082"]
      rc["ms-relacion-cliente<br/>:8083"]
    end
    kafka("Kafka 3.9.0 KRaft - agenda-kafka<br/>:9092 equipo / :29092 interno")
    kui["kafka-ui<br/>:8090 a 8080"]
    subgraph pg["agenda-postgres :5432 - base citas"]
      direction TB
      s1[("establecimientos")]
      s2[("agenda")]
      s3[("operaciones")]
      s4[("relacion_cliente")]
    end
  end
  nav -->|"HTTP :3000"| fe
  fe -->|"/graphql"| gw
  gw --> est
  gw --> ag
  gw --> op
  gw --> rc
  sim --> est
  sim --> ag
  sim --> op
  ag ==> kafka
  op ==> kafka
  rc ==> kafka
  kui ==> kafka
  est -.-> s1
  ag -.-> s2
  op -.-> s3
  rc -.-> s4
  subgraph leyenda["Leyenda"]
    direction LR
    l1["A"] -->|"REST / HTTP"| l2["B"]
    l3["A"] ==>|"Kafka"| l4["B"]
    l5["A"] -.->|"Base de datos"| l6["B"]
  end
  style kafka fill:#fff3cd,stroke:#b8860b,stroke-width:2px
  style kui fill:#fff3cd,stroke:#b8860b
  style java fill:#eef6ff,stroke:#4a78b5
  linkStyle 0,1,2,3,4,5,6,7,8 stroke:#1f4e8c,stroke-width:2px
  linkStyle 9,10,11,12 stroke:#d97706,stroke-width:4px
  linkStyle 13,14,15,16 stroke:#15803d,stroke-width:2px,stroke-dasharray:4 4
  linkStyle 17 stroke:#1f4e8c,stroke-width:2px
  linkStyle 18 stroke:#d97706,stroke-width:4px
  linkStyle 19 stroke:#15803d,stroke-width:2px,stroke-dasharray:4 4
```

![Arquitectura y despliegue](img/01-arquitectura.png)

Leyenda: flecha azul continua = REST/HTTP; flecha naranja gruesa = Kafka; flecha verde punteada = base de datos. Kafka se dibuja como rectángulo redondeado.

## Verificado contra

| Elemento | Dónde se comprobó |
|---|---|
| Puertos 3000, 8000, 8080-8084, 8090 (->8080 interno), 5432, 9092 | `ports:` de cada servicio en `docker-compose.yml` |
| Kafka `apache/kafka:3.9.0`, KRaft, listeners 29092 (interno) y 9092 (equipo) | servicio `kafka` en el compose |
| Postgres 16, base `citas`, volumen `agenda-data` | servicio `postgres` y `volumes:` |
| 4 esquemas: establecimientos, agenda, operaciones, relacion_cliente | `db-init/01-esquemas.sql` y `hibernate.default_schema` de cada `application.properties` |
| Esquema propio por servicio (ms-establecimientos, ms-agenda, ms-operaciones, ms-relacion-cliente) | `@Table(schema=...)` de las entidades |
| nginx envía `/graphql` al gateway (`gateway-graphql:8000`) | `frontend/nginx.conf` |
| Gateway llama a los 4 microservicios | `ServiciosInternos.java` y variables `*_URL` del compose |
| Simulador llama a establecimientos, agenda y operaciones | `ApiClient.java` y variables del compose |
| ms-agenda, ms-operaciones y ms-relacion-cliente usan Kafka; ms-establecimientos no | `KAFKA_SERVERS` solo en esos tres servicios |
| kafka-ui se conecta a `kafka:29092` | `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS` |

Nota: el comentario del compose dice que ms-agenda es el «único productor de eventos», pero ms-operaciones también produce `inventario.insumo-bajo` (`OperacionesService.java`). El diagrama sigue el código. kafka-ui es una herramienta de desarrollo, según el propio compose.
