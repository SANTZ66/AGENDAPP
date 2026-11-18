package com.fet.citas.agenda.api;

import com.fet.citas.agenda.dominio.Cita;
import com.fet.citas.agenda.dominio.CitaService;
import com.fet.citas.agenda.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API REST de citas. Cada metodo corresponde a un endpoint del contrato
 * OpenAPI (openapi/ms-agenda.yaml).
 *
 * El controlador solo se ocupa del transporte HTTP: recibe la peticion,
 * valida (@Valid), delega en CitaService y traduce el resultado a un
 * codigo HTTP. La logica vive en el servicio.
 */
@RestController
@RequestMapping("/citas")
@Tag(name = "Citas", description = "Reserva, cierre y cancelacion de citas")
public class CitaController {

    private final CitaService service;

    public CitaController(CitaService service) {
        this.service = service;
    }

    /** POST /citas -> 201 Created (o 409 si el cupo esta tomado). */
    @Operation(summary = "Reserva una cita. Transaccional (RF-10, RF-11)")
    @PostMapping
    public ResponseEntity<CitaResponse> reservar(
            @Valid @RequestBody ReservarCitaRequest req) {
        Cita cita = service.reservar(req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CitaResponse.desde(cita));
    }

    /**
     * GET /citas -> 200 OK con pagina de resultados.
     * Filtra por profesional y rango de fechas, con paginacion
     * (parametros page y size). Cubre el requisito "filtros y paginacion".
     */
    @Operation(summary = "Agenda de un profesional por rango de fechas (RF-26)")
    @GetMapping
    public Page<CitaResponse> agenda(
            @RequestParam UUID profesionalId,
            @RequestParam OffsetDateTime desde,
            @RequestParam OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("inicio").ascending());
        return service.agenda(profesionalId, desde, hasta, pageable)
                .map(CitaResponse::desde);
    }

    /**
     * GET /citas/proximas -> 200 OK con pagina de resultados.
     * Citas agendadas que empiezan en el rango, de cualquier profesional.
     * Las fechas se envian en UTC (terminadas en Z): un "+" en un offset
     * como +00:00 se pierde en la URL.
     */
    @Operation(summary = "Citas agendadas que empiezan en un rango, de cualquier profesional")
    @GetMapping("/proximas")
    public Page<CitaResponse> proximas(
            @RequestParam OffsetDateTime desde,
            @RequestParam OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("inicio").ascending());
        return service.proximas(desde, hasta, pageable)
                .map(CitaResponse::desde);
    }

    /** POST /citas/{id}/confirmacion -> 200 OK. */
    @Operation(summary = "La clienta confirma que asistira a la cita")
    @PostMapping("/{citaId}/confirmacion")
    public CitaResponse confirmar(@PathVariable UUID citaId) {
        return CitaResponse.desde(service.confirmar(citaId));
    }

    /** POST /citas/{id}/cierre -> 200 OK. */
    @Operation(summary = "Cierra la cita tras prestar el servicio (RF-14)")
    @PostMapping("/{citaId}/cierre")
    public CitaResponse cerrar(
            @PathVariable UUID citaId,
            @Valid @RequestBody CerrarCitaRequest req) {
        return CitaResponse.desde(service.cerrar(citaId, req));
    }

    /** POST /citas/{id}/cancelacion -> 200 OK. */
    @Operation(summary = "Cancela una cita y libera el cupo (RF-13)")
    @PostMapping("/{citaId}/cancelacion")
    public CitaResponse cancelar(
            @PathVariable UUID citaId,
            @Valid @RequestBody CancelarCitaRequest req) {
        return CitaResponse.desde(service.cancelar(citaId, req));
    }
}
