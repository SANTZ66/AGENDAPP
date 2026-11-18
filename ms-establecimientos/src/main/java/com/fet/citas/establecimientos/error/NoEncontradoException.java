package com.fet.citas.establecimientos.error;

public class NoEncontradoException extends RuntimeException {
    public NoEncontradoException(String que, Object id) {
        super(que + " " + id + " no existe");
    }
}
