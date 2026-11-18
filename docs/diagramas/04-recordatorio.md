# 4. Flujo de recordatorio y confirmación

```mermaid
sequenceDiagram
  autonumber
  participant J as GeneradorRecordatorios
  participant E as ms-establecimientos
  participant A as ms-agenda
  participant S as RelacionService
  participant C as Clienta
  participant G as gateway-graphql
  Note over J: Cada 60 s (espera inicial 30 s)
  J->>E: GET /establecimientos
  E-->>J: ventana de confirmacion de cada uno
  J->>A: GET /citas/proximas?desde&hasta
  A-->>J: citas agendadas (paginadas)
  loop cada cita
    Note over J: Omite si ya esta confirmada,<br/>el establecimiento no se conoce<br/>o esta fuera de la ventana
    J->>S: recordatorio(establecimientoId, citaId, inicio)
    Note over S: registrar(): si ya existe (cita_id, tipo)<br/>no inserta (UNIQUE en BD lo garantiza)
  end
  C->>G: confirmarCita(citaId), sin sesion
  G->>A: POST /citas/{id}/confirmacion
  A-->>G: cita con confirmadaEn
  G-->>C: Cita
  Note over A: No publica evento en Kafka
```

![Recordatorio](img/04-recordatorio.png)

## Verificado contra

| Elemento | Dónde se comprobó |
|---|---|
| Job `GeneradorRecordatorios`, `@Scheduled` fixedDelay 60000 ms, espera inicial 30000 ms, activo por defecto (`RECORDATORIOS_ACTIVO:true`) | `GeneradorRecordatorios.java`, `application.properties` de ms-relacion-cliente |
| `GET /establecimientos` para la ventana de confirmación de cada uno | `EstablecimientosClient.java` |
| `GET /citas/proximas?desde&hasta&page&size` | `AgendaClient.java` y `CitaController.java` de ms-agenda |
| Se omite si ya está confirmada, sin establecimiento conocido o fuera de la ventana | `GeneradorRecordatorios.revisar()` |
| `recordatorio()` llama a `registrar()`, que consulta `existsByCitaIdAndTipo`; tipo `recordatorio-cita` | `RelacionService.java` |
| `UNIQUE (cita_id, tipo)` (`notificacion_unica`); `DataIntegrityViolationException` se trata como ya registrada | `Notificacion.java`, `GeneradorRecordatorios.java` |
| `confirmarCita` no exige sesión | `ConsultasController.confirmarCita` (sin `Exigir`) |
| `POST /citas/{id}/confirmacion`; `confirmar()` es idempotente y no llama a `publicar()` | `CitaController.java`, `CitaService.confirmar` |

Nota: el comentario de `GeneradorRecordatorios.java` dice «Por defecto está apagado», pero `application.properties` lo deja encendido. El diagrama sigue el valor real. El comentario no se modificó.
