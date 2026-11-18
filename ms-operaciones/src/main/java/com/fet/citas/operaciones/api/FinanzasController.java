package com.fet.citas.operaciones.api;

import com.fet.citas.operaciones.dominio.Liquidacion;
import com.fet.citas.operaciones.dominio.OperacionesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El historico de ingresos que hoy no existe en el negocio (problema P4).
 * Se alimenta solo, a partir de los eventos: nadie lo registra a mano.
 */
@RestController
@Tag(name = "Finanzas", description = "Liquidaciones e historico de ingresos")
public class FinanzasController {

    private final OperacionesService service;

    public FinanzasController(OperacionesService service) {
        this.service = service;
    }

    @Operation(summary = "Servicios liquidados en un rango de fechas")
    @GetMapping("/liquidaciones")
    public List<LiquidacionDto> liquidaciones(
            @RequestParam UUID establecimientoId,
            @RequestParam OffsetDateTime desde,
            @RequestParam OffsetDateTime hasta) {
        return service.liquidaciones(establecimientoId, desde, hasta).stream()
                .map(LiquidacionDto::desde)
                .toList();
    }

    @Operation(summary = "Ingresos consolidados del periodo (RF-27)")
    @GetMapping("/resumen")
    public ResumenDto resumen(
            @RequestParam UUID establecimientoId,
            @RequestParam OffsetDateTime desde,
            @RequestParam OffsetDateTime hasta) {
        List<Liquidacion> lista = service.liquidaciones(establecimientoId, desde, hasta);
        BigDecimal total = service.totalIngresos(establecimientoId, desde, hasta);
        return new ResumenDto(establecimientoId, desde, hasta, lista.size(), total);
    }

    public record LiquidacionDto(UUID id, UUID citaId, UUID profesionalId, UUID servicioId,
                                 BigDecimal valor, OffsetDateTime fechaServicio) {
        public static LiquidacionDto desde(Liquidacion l) {
            return new LiquidacionDto(l.getId(), l.getCitaId(), l.getProfesionalId(),
                    l.getServicioId(), l.getValor(), l.getFechaServicio());
        }
    }

    public record ResumenDto(UUID establecimientoId, OffsetDateTime desde, OffsetDateTime hasta,
                             int serviciosPrestados, BigDecimal totalIngresos) {
    }
}
