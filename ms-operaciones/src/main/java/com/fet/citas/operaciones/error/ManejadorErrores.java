package com.fet.citas.operaciones.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/** Manejo centralizado de errores. */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                detalles.put(e.getField(), e.getDefaultMessage()));
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("timestamp", OffsetDateTime.now());
        cuerpo.put("status", 400);
        cuerpo.put("error", "Bad Request");
        cuerpo.put("mensaje", "La peticion tiene datos invalidos");
        cuerpo.put("detalles", detalles);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }
}
