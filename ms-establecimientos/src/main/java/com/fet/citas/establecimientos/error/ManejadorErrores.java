package com.fet.citas.establecimientos.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/** Manejo centralizado de errores (mismo patron que ms-agenda). */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                detalles.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.de(400, "Bad Request",
                        "La peticion tiene datos invalidos", detalles));
    }

    @ExceptionHandler(NoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(NoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.de(404, "Not Found", ex.getMessage()));
    }
}
