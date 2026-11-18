package com.fet.citas.gateway.seguridad;

/** Falta la sesion, esta vencida o las credenciales no son correctas. */
public class NoAutenticadoException extends RuntimeException {

    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
