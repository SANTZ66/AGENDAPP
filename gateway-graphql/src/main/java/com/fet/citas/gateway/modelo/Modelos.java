package com.fet.citas.gateway.modelo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Las formas que el gateway lee de los servicios internos y expone en
 * GraphQL. Las fechas viajan como texto para no depender de scalars
 * adicionales: GraphQL no trae un tipo de fecha propio.
 */
public final class Modelos {

    private Modelos() {
    }

    public record Establecimiento(UUID id, String nombre, String telefono,
                                  Integer ventanaConfirmacionHoras) {
    }

    public record Profesional(UUID id, UUID establecimientoId, String nombre, Boolean activo) {
    }

    public record Servicio(UUID id, UUID establecimientoId, String nombre,
                           Integer duracionMinutos, BigDecimal precio,
                           Integer periodicidadDias) {
    }

    public record Cita(UUID id, UUID establecimientoId, UUID profesionalId, UUID servicioId,
                       UUID clienteId, String inicio, String fin, String estado,
                       BigDecimal valorCobrado, String confirmadaEn) {
    }

    /** Una pagina de Spring Data, tal como la devuelve ms-agenda. */
    public record PaginaCitas(List<Cita> content, Integer totalElements) {
    }

    /**
     * El resultado de juntar dos servicios: la cita viene de ms-agenda y el
     * nombre del servicio de ms-establecimientos. Es el caso que justifica
     * que exista el gateway.
     */
    public record CitaDetallada(UUID id, String inicio, String fin, String estado,
                                UUID clienteId, String servicio, Integer duracionMinutos,
                                BigDecimal valorCobrado, String confirmadaEn) {
    }

    public record Insumo(UUID id, UUID establecimientoId, String nombre, String unidad,
                         BigDecimal existencia, BigDecimal umbralMinimo, Boolean enUmbral) {
    }

    public record Resumen(UUID establecimientoId, String desde, String hasta,
                          Integer serviciosPrestados, BigDecimal totalIngresos) {
    }

    /** Cada elemento de la lista que devuelve ms-agenda en GET /disponibilidad. */
    public record Franja(String inicio, String fin) {
    }

    public record Recurrencia(UUID clienteId, String ultimaVisita, String proximoContacto,
                              Integer totalVisitas, Integer totalCancelaciones) {
    }

    public record Notificacion(UUID id, UUID citaId, String tipo, String canal,
                               String asunto, String cuerpo, String estado, String creadaEn) {
    }
}
