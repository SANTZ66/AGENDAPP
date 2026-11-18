package com.fet.citas.agenda.dto;

import java.time.OffsetDateTime;

/** Una franja horaria libre para agendar. */
public record FranjaDisponible(OffsetDateTime inicio, OffsetDateTime fin) {
}
