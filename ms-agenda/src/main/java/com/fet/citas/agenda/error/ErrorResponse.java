package com.fet.citas.agenda.error;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Formato uniforme de todos los errores de la API. Que todos los errores
 * tengan la misma forma facilita al cliente manejarlos (requisito
 * "manejo centralizado de errores").
 */
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String mensaje,
        Map<String, String> detalles
) {
    public static ErrorResponse de(int status, String error, String mensaje) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, mensaje, null);
    }

    public static ErrorResponse de(int status, String error, String mensaje,
                                   Map<String, String> detalles) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, mensaje, detalles);
    }
}
