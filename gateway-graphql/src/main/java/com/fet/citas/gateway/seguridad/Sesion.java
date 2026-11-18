package com.fet.citas.gateway.seguridad;

import java.time.Instant;
import java.util.UUID;

/** Lo que dice un token valido: quien es, de que establecimiento y hasta cuando. */
public record Sesion(UUID usuarioId, UUID establecimientoId,
                     String nombre, String rol, Instant expiraEn) {
}
