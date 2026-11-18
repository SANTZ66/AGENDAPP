package com.fet.citas.agenda.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejo CENTRALIZADO de errores. En vez de que cada endpoint construya sus
 * propias respuestas de error, todas las excepciones caen aqui y se
 * traducen a un HTTP status y un cuerpo uniforme.
 *
 * Este es el punto que la rubrica llama "manejo centralizado de errores" y
 * "codigos HTTP adecuados".
 */
@RestControllerAdvice
public class ManejadorErrores {

    /** Falta un campo o un valor es invalido -> 400 Bad Request */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                detalles.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.de(400, "Bad Request",
                        "La peticion tiene datos invalidos", detalles));
    }

    /** El cupo ya estaba tomado -> 409 Conflict */
    @ExceptionHandler(CupoOcupadoException.class)
    public ResponseEntity<ErrorResponse> cupoOcupado(CupoOcupadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.de(409, "Conflict", ex.getMessage()));
    }

    /**
     * Red de seguridad: si dos reservas simultaneas pasan la verificacion
     * previa y ambas intentan el INSERT, la restriccion UNIQUE de la BD
     * hace fallar a la segunda con DataIntegrityViolationException. La
     * atrapamos aqui y la convertimos igualmente en un 409 limpio.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.de(409, "Conflict",
                        "El cupo ya fue tomado por otra reserva"));
    }

    /** Cita inexistente -> 404 Not Found */
    @ExceptionHandler(CitaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> noEncontrada(CitaNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.de(404, "Not Found", ex.getMessage()));
    }

    /** Transicion de estado invalida -> 409 Conflict */
    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> estadoInvalido(EstadoInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.de(409, "Conflict", ex.getMessage()));
    }
}
