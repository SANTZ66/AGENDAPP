package com.fet.citas.relacion.api;

import com.fet.citas.relacion.dominio.ClienteRecurrencia;
import com.fet.citas.relacion.dominio.Notificacion;
import com.fet.citas.relacion.dominio.RelacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Consulta de notificaciones y del historial de recurrencia. */
@RestController
@Tag(name = "Relacion con el cliente",
     description = "Notificaciones, recurrencia y clientes inactivos")
public class RelacionController {

    private final RelacionService service;

    public RelacionController(RelacionService service) {
        this.service = service;
    }

    @Operation(summary = "Notificaciones generadas para el establecimiento (RF-24, RF-25)")
    @GetMapping("/notificaciones")
    public List<NotificacionDto> notificaciones(@RequestParam UUID establecimientoId) {
        return service.notificaciones(establecimientoId).stream()
                .map(NotificacionDto::desde)
                .toList();
    }

    @Operation(summary = "Historial y proximo contacto de un cliente (RF-21)")
    @GetMapping("/clientes/{clienteId}/recurrencia")
    public RecurrenciaDto recurrencia(@PathVariable UUID clienteId,
                                      @RequestParam UUID establecimientoId) {
        ClienteRecurrencia r = service.recurrencia(establecimientoId, clienteId);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El cliente " + clienteId + " no tiene historial en este establecimiento");
        }
        return RecurrenciaDto.desde(r);
    }

    @Operation(summary = "Clientes que no han vuelto segun su periodicidad (RF-28)")
    @GetMapping("/clientes-inactivos")
    public List<RecurrenciaDto> inactivos(@RequestParam UUID establecimientoId) {
        return service.inactivos(establecimientoId).stream()
                .map(RecurrenciaDto::desde)
                .toList();
    }

    public record NotificacionDto(UUID id, UUID citaId, String tipo, String canal,
                                  String asunto, String cuerpo, String estado,
                                  OffsetDateTime creadaEn) {
        public static NotificacionDto desde(Notificacion n) {
            return new NotificacionDto(n.getId(), n.getCitaId(), n.getTipo(), n.getCanal(),
                    n.getAsunto(), n.getCuerpo(), n.getEstado(), n.getCreadaEn());
        }
    }

    public record RecurrenciaDto(UUID clienteId, OffsetDateTime ultimaVisita,
                                 OffsetDateTime proximoContacto, int totalVisitas,
                                 int totalCancelaciones) {
        public static RecurrenciaDto desde(ClienteRecurrencia c) {
            return new RecurrenciaDto(c.getClienteId(), c.getUltimaVisita(),
                    c.getProximoContacto(), c.getTotalVisitas(), c.getTotalCancelaciones());
        }
    }
}
