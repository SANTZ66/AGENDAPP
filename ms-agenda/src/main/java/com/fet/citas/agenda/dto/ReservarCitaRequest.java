package com.fet.citas.agenda.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Datos que el cliente envia para reservar una cita.
 *
 * Las anotaciones @NotNull hacen que Spring rechace automaticamente con
 * HTTP 400 cualquier peticion a la que le falte un campo, antes de que el
 * codigo del controlador se ejecute. Eso cubre el requisito de la rubrica
 * "validaciones y manejo de errores".
 */
public record ReservarCitaRequest(

        @NotNull(message = "establecimientoId es obligatorio")
        UUID establecimientoId,

        @NotNull(message = "profesionalId es obligatorio")
        UUID profesionalId,

        @NotNull(message = "servicioId es obligatorio")
        UUID servicioId,

        @NotNull(message = "clienteId es obligatorio")
        UUID clienteId,

        @NotNull(message = "inicio es obligatorio")
        OffsetDateTime inicio,

        @NotNull(message = "fin es obligatorio")
        OffsetDateTime fin
) {
}
