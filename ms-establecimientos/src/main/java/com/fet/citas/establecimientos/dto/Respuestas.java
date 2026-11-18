package com.fet.citas.establecimientos.dto;

import com.fet.citas.establecimientos.dominio.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Lo que la API devuelve. Separado de las entidades JPA a proposito. */
public final class Respuestas {

    private Respuestas() {
    }

    public record EstablecimientoDto(UUID id, String nombre, String telefono,
                                     int ventanaConfirmacionHoras) {
        public static EstablecimientoDto desde(Establecimiento e) {
            return new EstablecimientoDto(e.getId(), e.getNombre(),
                    e.getTelefono(), e.getVentanaConfirmacionHoras());
        }
    }

    public record ProfesionalDto(UUID id, UUID establecimientoId,
                                 String nombre, boolean activo) {
        public static ProfesionalDto desde(Profesional p) {
            return new ProfesionalDto(p.getId(), p.getEstablecimientoId(),
                    p.getNombre(), p.isActivo());
        }
    }

    public record ServicioDto(UUID id, UUID establecimientoId, String nombre,
                              int duracionMinutos, BigDecimal precio,
                              Integer periodicidadDias) {
        public static ServicioDto desde(Servicio s) {
            return new ServicioDto(s.getId(), s.getEstablecimientoId(), s.getNombre(),
                    s.getDuracionMinutos(), s.getPrecio(), s.getPeriodicidadDias());
        }
    }

    /** Resultado de verificar credenciales. Nunca incluye la clave ni su hash. */
    public record UsuarioVerificado(UUID usuarioId, String nombre,
                                    UUID establecimientoId, String rol) {
        public static UsuarioVerificado desde(Usuario u) {
            return new UsuarioVerificado(u.getId(), u.getNombre(),
                    u.getEstablecimientoId(), u.getRol());
        }
    }

    /**
     * La receta de un servicio. Es la respuesta que consume ms-operaciones
     * cuando necesita saber que descontar del inventario.
     */
    public record InsumoDeServicioDto(UUID insumoId, BigDecimal cantidadEstimada) {
        public static InsumoDeServicioDto desde(ServicioInsumo si) {
            return new InsumoDeServicioDto(si.getInsumoId(), si.getCantidadEstimada());
        }
    }
}
