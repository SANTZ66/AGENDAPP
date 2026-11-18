package com.fet.citas.gateway.seguridad;

/**
 * El token no se pudo aceptar. "vencido" distingue el caso normal (la sesion
 * duro lo que debia) de un token alterado o mal formado.
 */
public class TokenInvalidoException extends RuntimeException {

    private final boolean vencido;

    public TokenInvalidoException(String mensaje, boolean vencido, Throwable causa) {
        super(mensaje, causa);
        this.vencido = vencido;
    }

    public boolean vencido() {
        return vencido;
    }
}
