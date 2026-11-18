package com.fet.citas.relacion.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Forma del evento que publica ms-operaciones cuando hay que reponer. */
public record EventoInsumoBajo(
        UUID insumoId,
        UUID establecimientoId,
        String nombre,
        BigDecimal existencia,
        BigDecimal umbralMinimo,
        OffsetDateTime ocurridoEn) {
}
