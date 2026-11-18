package com.fet.citas.establecimientos.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpos de entrada de la API. Son records con validaciones: si falta un
 * campo, Spring devuelve 400 antes de que el controlador se ejecute.
 */
public final class Peticiones {

    private Peticiones() {
    }

    public record NuevoEstablecimiento(
            @NotBlank(message = "nombre es obligatorio") String nombre,
            String telefono,
            @Min(value = 1, message = "la ventana debe ser de al menos 1 hora")
            Integer ventanaConfirmacionHoras) {
    }

    public record NuevoProfesional(
            @NotBlank(message = "nombre es obligatorio") String nombre) {
    }

    public record NuevoServicio(
            @NotNull(message = "establecimientoId es obligatorio") UUID establecimientoId,
            @NotBlank(message = "nombre es obligatorio") String nombre,
            @NotNull(message = "duracionMinutos es obligatorio")
            @Min(value = 5, message = "la duracion minima es 5 minutos") Integer duracionMinutos,
            @NotNull(message = "precio es obligatorio")
            @DecimalMin(value = "0.0", message = "el precio no puede ser negativo") BigDecimal precio,
            Integer periodicidadDias) {
    }

    public record Credenciales(
            @NotBlank(message = "correo es obligatorio") String correo,
            @NotBlank(message = "clave es obligatoria") String clave) {
    }

    public record NuevoInsumoDeServicio(
            @NotNull(message = "insumoId es obligatorio") UUID insumoId,
            @NotNull(message = "cantidadEstimada es obligatorio")
            @DecimalMin(value = "0.0", inclusive = false,
                    message = "la cantidad debe ser mayor que cero") BigDecimal cantidadEstimada) {
    }
}
