package com.fet.citas.operaciones.api;

import com.fet.citas.operaciones.dominio.Insumo;
import com.fet.citas.operaciones.persistencia.InsumoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Existencias de insumos y alertas de reposicion. */
@RestController
@RequestMapping("/inventario")
@Tag(name = "Inventario", description = "Existencias de insumos y alertas de reposicion")
public class InventarioController {

    private final InsumoRepository insumos;

    public InventarioController(InsumoRepository insumos) {
        this.insumos = insumos;
    }

    @Operation(summary = "Registra un insumo con su umbral de reposicion (RF-07)")
    @PostMapping
    public ResponseEntity<InsumoDto> crear(@Valid @RequestBody NuevoInsumo req) {
        Insumo guardado = insumos.save(new Insumo(
                req.establecimientoId(), req.nombre(), req.unidad(),
                req.existencia(), req.umbralMinimo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(InsumoDto.desde(guardado));
    }

    @Operation(summary = "Existencias del establecimiento")
    @GetMapping
    public List<InsumoDto> listar(@RequestParam UUID establecimientoId) {
        return insumos.findByEstablecimientoId(establecimientoId).stream()
                .map(InsumoDto::desde)
                .toList();
    }

    @Operation(summary = "Ajuste manual de existencias o umbral")
    @PutMapping("/{insumoId}")
    public InsumoDto ajustar(@PathVariable UUID insumoId, @RequestBody AjusteInsumo req) {
        Insumo insumo = insumos.findById(insumoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "El insumo " + insumoId + " no existe"));
        insumo.ajustar(req.existencia(), req.umbralMinimo());
        return InsumoDto.desde(insumos.save(insumo));
    }

    @Operation(summary = "Insumos que alcanzaron su umbral minimo (RF-20)")
    @GetMapping("/alertas")
    public List<InsumoDto> alertas(@RequestParam UUID establecimientoId) {
        return insumos.findEnUmbral(establecimientoId).stream()
                .map(InsumoDto::desde)
                .toList();
    }

    public record NuevoInsumo(
            @NotNull(message = "establecimientoId es obligatorio") UUID establecimientoId,
            @NotBlank(message = "nombre es obligatorio") String nombre,
            String unidad,
            @NotNull(message = "existencia es obligatorio") BigDecimal existencia,
            @NotNull(message = "umbralMinimo es obligatorio") BigDecimal umbralMinimo) {
    }

    public record AjusteInsumo(BigDecimal existencia, BigDecimal umbralMinimo) {
    }

    public record InsumoDto(UUID id, UUID establecimientoId, String nombre, String unidad,
                            BigDecimal existencia, BigDecimal umbralMinimo, boolean enUmbral) {
        public static InsumoDto desde(Insumo i) {
            return new InsumoDto(i.getId(), i.getEstablecimientoId(), i.getNombre(),
                    i.getUnidad(), i.getExistencia(), i.getUmbralMinimo(), i.enUmbral());
        }
    }
}
