package com.fet.citas.agenda.error;

/**
 * Se lanza al intentar una transicion no permitida, p. ej. cerrar una cita
 * ya cancelada. -> HTTP 409
 */
public class EstadoInvalidoException extends RuntimeException {
    public EstadoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
