package com.fet.citas.agenda.error;

/** Se lanza cuando el profesional ya tiene una cita a esa hora. -> HTTP 409 */
public class CupoOcupadoException extends RuntimeException {
    public CupoOcupadoException(String mensaje) {
        super(mensaje);
    }
}
