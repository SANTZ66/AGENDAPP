package com.fet.citas.agenda.error;

import java.util.UUID;

/** Se lanza cuando se busca una cita que no existe. -> HTTP 404 */
public class CitaNoEncontradaException extends RuntimeException {
    public CitaNoEncontradaException(UUID id) {
        super("No existe una cita con id " + id);
    }
}
