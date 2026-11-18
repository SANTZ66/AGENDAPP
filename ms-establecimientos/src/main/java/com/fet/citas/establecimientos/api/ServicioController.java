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

/**
 * Catalogo de servicios y su receta de insumos.
 *
 * GET /servicios/{id} lo consulta ms-agenda para conocer la duracion.
 * GET /servicios/{id}/insumos lo consulta ms-operaciones para saber que
 * descontar del inventario. Son las dos llamadas REST entre servicios.
 */
@RestController
@RequestMapping("/servicios")
@Tag(name = "Servicios", description = "Catalogo de servicios con duracion, precio e insumos")
public class ServicioController {

    private final ServicioRepository servicios;
    private final ServicioInsumoRepository insumos;

    public ServicioController(ServicioRepository servicios, ServicioInsumoRepository insumos) {
        this.servicios = servicios;
        this.insumos = insumos;
    }

    @Operation(summary = "Define un servicio con duracion y precio (RF-05)")
    @PostMapping
    public ResponseEntity<ServicioDto> crear(@Valid @RequestBody NuevoServicio req) {
        Servicio guardado = servicios.save(new Servicio(
                req.establecimientoId(), req.nombre(), req.duracionMinutos(),
                req.precio(), req.periodicidadDias()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ServicioDto.desde(guardado));
    }

    @Operation(summary = "Lista el catalogo de un establecimiento")
    @GetMapping
    public List<ServicioDto> listar(@RequestParam UUID establecimientoId) {
        return servicios.findByEstablecimientoId(establecimientoId).stream()
                .map(ServicioDto::desde)
                .toList();
    }

    @Operation(summary = "Consulta un servicio. Lo usa ms-agenda para la duracion")
    @GetMapping("/{servicioId}")
    public ServicioDto obtener(@PathVariable UUID servicioId) {
        return servicios.findById(servicioId)
                .map(ServicioDto::desde)
                .orElseThrow(() -> new NoEncontradoException("Servicio", servicioId));
    }

    @Operation(summary = "Asocia un insumo al servicio con su cantidad estimada (RF-06)")
    @PostMapping("/{servicioId}/insumos")
    public ResponseEntity<InsumoDeServicioDto> asociarInsumo(
            @PathVariable UUID servicioId,
            @Valid @RequestBody NuevoInsumoDeServicio req) {
        if (!servicios.existsById(servicioId)) {
            throw new NoEncontradoException("Servicio", servicioId);
        }
        ServicioInsumo guardado = insumos.save(
                new ServicioInsumo(servicioId, req.insumoId(), req.cantidadEstimada()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InsumoDeServicioDto.desde(guardado));
    }

    @Operation(summary = "Receta del servicio. La consulta ms-operaciones por REST")
    @GetMapping("/{servicioId}/insumos")
    public List<InsumoDeServicioDto> receta(@PathVariable UUID servicioId) {
        return insumos.findByServicioId(servicioId).stream()
                .map(InsumoDeServicioDto::desde)
                .toList();
    }
}
