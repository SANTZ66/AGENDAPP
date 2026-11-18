package com.fet.citas.gateway.seguridad;

import java.util.UUID;

/**
 * Las dos preguntas que se hacen las operaciones protegidas: hay sesion, y la
 * sesion es del establecimiento consultado.
 *
 * Sin sesion lanza NoAutenticadoException (UNAUTHORIZED); con sesion de otro
 * establecimiento lanza SinPermisoException (FORBIDDEN).
 */
public final class Exigir {

    private Exigir() {
    }

    public static Sesion sesion(Sesion sesion, String motivo) {
        if (sesion == null) {
            throw new NoAutenticadoException(
                    InterceptorSesion.VENCIDA.equals(motivo)
                            ? "La sesion vencio, inicia sesion de nuevo"
                            : "Inicia sesion para continuar");
        }
        return sesion;
    }

    public static Sesion establecimiento(Sesion sesion, String motivo, UUID establecimientoId) {
        Sesion s = sesion(sesion, motivo);
        if (!s.establecimientoId().equals(establecimientoId)) {
            throw new SinPermisoException("No tienes acceso a ese establecimiento");
        }
        return s;
    }
}
