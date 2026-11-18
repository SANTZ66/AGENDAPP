package com.fet.citas.operaciones.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Copia local de la forma del evento que publica ms-agenda.
 *
 * Esta duplicacion es deliberada: si los servicios compartieran una
 * libreria de eventos, un cambio en ella obligaria a recompilar y
 * desplegar todos a la vez, que es justo lo que la arquitectura de
 * eventos quiere evitar. Cada consumidor declara solo lo que necesita
 * leer y los campos que no reconoce se ignoran.
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
