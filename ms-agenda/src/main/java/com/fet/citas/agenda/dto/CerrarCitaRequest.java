package com.fet.citas.agenda.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Datos para cerrar una cita: cuanto se cobro por el servicio prestado. */
public record CerrarCitaRequest(

        @NotNull(message = "valorCobrado es obligatorio")
        @PositiveOrZero(message = "valorCobrado no puede ser negativo")
        BigDecimal valorCobrado
) {
}
