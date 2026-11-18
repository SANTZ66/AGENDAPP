package com.fet.citas.establecimientos.error;

import java.time.OffsetDateTime;
import java.util.Map;

/** Cuerpo uniforme de error, igual en todos los servicios. */
public record ErrorResponse(OffsetDateTime timestamp, int status, String error,
                            String mensaje, Map<String, String> detalles) {

    public static ErrorResponse de(int status, String error, String mensaje) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, mensaje, null);
    }

    public static ErrorResponse de(int status, String error, String mensaje,
                                   Map<String, String> detalles) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, mensaje, detalles);
    }
}
