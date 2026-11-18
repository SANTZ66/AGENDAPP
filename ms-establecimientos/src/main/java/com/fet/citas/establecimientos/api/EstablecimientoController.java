package com.fet.citas.establecimientos.api;

import com.fet.citas.establecimientos.dominio.*;
import com.fet.citas.establecimientos.dto.Peticiones.*;
import com.fet.citas.establecimientos.dto.Respuestas.*;
import com.fet.citas.establecimientos.error.NoEncontradoException;
import com.fet.citas.establecimientos.persistencia.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Alta y consulta de establecimientos y de sus profesionales. */
@RestController
@RequestMapping("/establecimientos")
@Tag(name = "Establecimientos", description = "Inquilinos de la plataforma y sus profesionales")
public class EstablecimientoController {

    private final EstablecimientoRepository establecimientos;
    private final ProfesionalRepository profesionales;

    public EstablecimientoController(EstablecimientoRepository establecimientos,
                                     ProfesionalRepository profesionales) {
        this.establecimientos = establecimientos;
        this.profesionales = profesionales;
    }

    @Operation(summary = "Registra un establecimiento (RF-01)")
    @PostMapping
    public ResponseEntity<EstablecimientoDto> crear(@Valid @RequestBody NuevoEstablecimiento req) {
        int ventana = req.ventanaConfirmacionHoras() == null ? 24 : req.ventanaConfirmacionHoras();
        Establecimiento guardado = establecimientos.save(
                new Establecimiento(req.nombre(), req.telefono(), ventana));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EstablecimientoDto.desde(guardado));
    }

    @Operation(summary = "Lista los establecimientos registrados")
    @GetMapping
    public List<EstablecimientoDto> listar() {
        return establecimientos.findAll().stream()
                .map(EstablecimientoDto::desde)
                .toList();
    }

    @Operation(summary = "Consulta la configuracion de un establecimiento")
    @GetMapping("/{establecimientoId}")
    public EstablecimientoDto obtener(@PathVariable UUID establecimientoId) {
        return establecimientos.findById(establecimientoId)
                .map(EstablecimientoDto::desde)
                .orElseThrow(() -> new NoEncontradoException("Establecimiento", establecimientoId));
    }

    @Operation(summary = "Registra un profesional del establecimiento (RF-02)")
    @PostMapping("/{establecimientoId}/profesionales")
    public ResponseEntity<ProfesionalDto> crearProfesional(
            @PathVariable UUID establecimientoId,
            @Valid @RequestBody NuevoProfesional req) {
        if (!establecimientos.existsById(establecimientoId)) {
            throw new NoEncontradoException("Establecimiento", establecimientoId);
        }
        Profesional guardado = profesionales.save(
                new Profesional(establecimientoId, req.nombre()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfesionalDto.desde(guardado));
    }

    @Operation(summary = "Lista los profesionales del establecimiento")
    @GetMapping("/{establecimientoId}/profesionales")
    public List<ProfesionalDto> listarProfesionales(@PathVariable UUID establecimientoId) {
        return profesionales.findByEstablecimientoId(establecimientoId).stream()
                .map(ProfesionalDto::desde)
                .toList();
    }
}
