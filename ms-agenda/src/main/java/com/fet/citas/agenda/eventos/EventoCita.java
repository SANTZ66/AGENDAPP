package com.fet.citas.agenda.eventos;

import com.fet.citas.agenda.dominio.Cita;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que viaja por el bus cuando algo le pasa a una cita.
 *
 * Es una foto de la cita en el momento del hecho, no una referencia: el
 * consumidor no tiene que volver a preguntarle nada a ms-agenda para
 * procesarlo. Ese es el punto del desacoplamiento.
 *
 * Todos los topics de citas usan esta misma forma. Los campos que no
 * aplican a un hecho concreto viajan en null (por ejemplo valorCobrado
 * solo tiene valor en citas.completada).
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

    public static EventoCita desde(Cita cita, String motivo) {
        return new EventoCita(
                cita.getId(),
                cita.getEstablecimientoId(),
                cita.getProfesionalId(),
                cita.getServicioId(),
                cita.getClienteId(),
                cita.getInicio(),
                cita.getFin(),
                cita.getEstado().name().toLowerCase(),
                cita.getValorCobrado(),
                motivo,
                OffsetDateTime.now());
    }

    public static EventoCita desde(Cita cita) {
        return desde(cita, null);
    }
}
