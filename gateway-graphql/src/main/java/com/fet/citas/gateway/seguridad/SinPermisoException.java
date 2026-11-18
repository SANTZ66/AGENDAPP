package com.fet.citas.gateway.seguridad;

/** La sesion es valida, pero no da acceso a lo que se pidio. */
public class SinPermisoException extends RuntimeException {

    public SinPermisoException(String mensaje) {
        super(mensaje);
    }
}
