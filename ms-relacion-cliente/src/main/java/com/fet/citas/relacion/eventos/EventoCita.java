package com.fet.citas.relacion.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Copia local de la forma del evento de ms-agenda. Igual que en
 * ms-operaciones: cada consumidor declara lo que lee, sin libreria
 * compartida que acople los despliegues.
 */
public record EventoCita(
        UUID citaId,
        UUID establecimientoId,
        UUID profesionalId,
        UUID servicioId,
        UUID clienteId,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String estado,
        BigDecimal valorCobrado,
        String motivo,
        OffsetDateTime ocurridoEn) {
}
