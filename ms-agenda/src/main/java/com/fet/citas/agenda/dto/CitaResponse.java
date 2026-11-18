package com.fet.citas.agenda.dto;

import com.fet.citas.agenda.dominio.Cita;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vista de una cita que se devuelve al cliente.
 *
 * Es un DTO separado de la entidad Cita a proposito: la entidad es como
 * guardamos los datos por dentro; el DTO es el contrato con quien consume
 * la API. Separarlos permite cambiar uno sin romper el otro (requisito
 * "DTO" de la rubrica).
 */
public record CitaResponse(
        UUID id,
        UUID establecimientoId,
        UUID profesionalId,
        UUID servicioId,
        UUID clienteId,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String estado,
        BigDecimal valorCobrado,
        OffsetDateTime confirmadaEn
) {
    /** Convierte una entidad de dominio en su representacion de salida. */
    public static CitaResponse desde(Cita c) {
        return new CitaResponse(
                c.getId(),
                c.getEstablecimientoId(),
                c.getProfesionalId(),
                c.getServicioId(),
                c.getClienteId(),
                c.getInicio(),
                c.getFin(),
                c.getEstado().name().toLowerCase(),
                c.getValorCobrado(),
                c.getConfirmadaEn()
        );
    }
}
