package com.fet.citas.agenda.api;

import com.fet.citas.agenda.dominio.CitaService;
import com.fet.citas.agenda.dto.FranjaDisponible;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /disponibilidad -> franjas libres de un profesional en una fecha.
 *
 * En esta entrega la jornada es fija (09:00 a 18:00 en franjas de una hora)
 * y se descartan las horas que ya tengan una cita. Es deliberadamente
 * simple: la reserva real y su control de concurrencia estan en
 * CitaController; aqui solo se ofrece la consulta previa.
 */
@RestController
@RequestMapping("/disponibilidad")
@Tag(name = "Disponibilidad", description = "Consulta de cupos libres")
public class DisponibilidadController {

    private final CitaService service;

    public DisponibilidadController(CitaService service) {
        this.service = service;
    }

    @Operation(summary = "Cupos libres de un profesional en una fecha (RF-09)")
    @GetMapping
    public List<FranjaDisponible> disponibilidad(
            @RequestParam UUID profesionalId,
            @RequestParam LocalDate fecha) {
        return service.franjasLibres(profesionalId, fecha);
    }
}
