package com.fet.citas.agenda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Datos para cancelar una cita. */
public record CancelarCitaRequest(

        @NotBlank(message = "canceladaPor es obligatorio")
        @Pattern(regexp = "cliente|profesional",
                 message = "canceladaPor debe ser 'cliente' o 'profesional'")
        String canceladaPor,

        String motivo
) {
}
