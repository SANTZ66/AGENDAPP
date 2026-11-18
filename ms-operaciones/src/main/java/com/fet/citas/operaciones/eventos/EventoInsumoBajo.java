package com.fet.citas.operaciones.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que ms-operaciones publica cuando un insumo llega a su umbral.
 *
 * Es el caso que demuestra que un consumidor puede a su vez ser productor,
 * sin que eso lo acople al servicio que origino el evento inicial.
 */
public record EventoInsumoBajo(
        UUID insumoId,
        UUID establecimientoId,
        String nombre,
        BigDecimal existencia,
        BigDecimal umbralMinimo,
        OffsetDateTime ocurridoEn) {
}
