package com.fet.citas.gateway.api;

import com.fet.citas.gateway.clientes.ServiciosInternos;
import com.fet.citas.gateway.modelo.Modelos.*;
import com.fet.citas.gateway.seguridad.Exigir;
import com.fet.citas.gateway.seguridad.InterceptorSesion;
import com.fet.citas.gateway.seguridad.Sesion;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El esquema GraphQL implementado sobre las APIs REST internas.
 *
 * Fijate en agenda(): una sola consulta del cliente se convierte en varias
 * llamadas internas y vuelve como un unico resultado. Sin gateway, el
 * cliente tendria que encadenar esas llamadas y componerlas el mismo.
 */
@Controller
public class ConsultasController {

    private final ServiciosInternos servicios;

    public ConsultasController(ServiciosInternos servicios) {
        this.servicios = servicios;
    }

    // ---------- Consultas ----------

    @QueryMapping
    public List<Establecimiento> establecimientos() {
        Establecimiento[] r = servicios.establecimientos.get()
                .uri("/establecimientos")
                .retrieve().body(Establecimiento[].class);
        return r == null ? List.of() : List.of(r);
    }

    @QueryMapping
    public List<Profesional> profesionales(@Argument UUID establecimientoId) {
        Profesional[] r = servicios.establecimientos.get()
                .uri("/establecimientos/{id}/profesionales", establecimientoId)
                .retrieve().body(Profesional[].class);
        return r == null ? List.of() : List.of(r);
    }

    @QueryMapping
    public List<Servicio> servicios(@Argument UUID establecimientoId) {
        Servicio[] r = servicios.establecimientos.get()
                .uri(b -> b.path("/servicios")
                        .queryParam("establecimientoId", establecimientoId).build())
                .retrieve().body(Servicio[].class);
        return r == null ? List.of() : List.of(r);
    }

    /**
     * La consulta que justifica el gateway: agrega ms-agenda con
     * ms-establecimientos para devolver la agenda con el NOMBRE del
     * servicio, que ms-agenda no conoce.
     */
    @QueryMapping
    public List<CitaDetallada> agenda(@Argument UUID profesionalId,
                                      @Argument String desde,
                                      @Argument String hasta) {
        PaginaCitas pagina = servicios.agenda.get()
                .uri(b -> b.path("/citas")
                        .queryParam("profesionalId", profesionalId)
                        .queryParam("desde", desde)
                        .queryParam("hasta", hasta)
                        .queryParam("size", 100)
                        .build())
                .retrieve().body(PaginaCitas.class);

        if (pagina == null || pagina.content() == null) {
            return List.of();
        }

        // Un solo viaje por servicio distinto, no uno por cita.
        Map<UUID, Servicio> cache = new HashMap<>();
        return pagina.content().stream().map(cita -> {
            Servicio s = cache.computeIfAbsent(cita.servicioId(), this::buscarServicio);
            return new CitaDetallada(
                    cita.id(), cita.inicio(), cita.fin(), cita.estado(), cita.clienteId(),
                    s == null ? "(desconocido)" : s.nombre(),
                    s == null ? null : s.duracionMinutos(),
                    cita.valorCobrado(),
                    cita.confirmadaEn());
        }).toList();
    }

    @QueryMapping
    public List<Insumo> inventario(@Argument UUID establecimientoId,
                                   @ContextValue(name = InterceptorSesion.SESION, required = false) Sesion sesion,
                                   @ContextValue(name = InterceptorSesion.SESION_ERROR, required = false) String motivo) {
        Exigir.establecimiento(sesion, motivo, establecimientoId);
        Insumo[] r = servicios.operaciones.get()
                .uri(b -> b.path("/inventario")
                        .queryParam("establecimientoId", establecimientoId).build())
                .retrieve().body(Insumo[].class);
        return r == null ? List.of() : List.of(r);
    }

    @QueryMapping
    public Resumen resumenIngresos(@Argument UUID establecimientoId,
                                   @Argument String desde,
                                   @Argument String hasta,
                                   @ContextValue(name = InterceptorSesion.SESION, required = false) Sesion sesion,
                                   @ContextValue(name = InterceptorSesion.SESION_ERROR, required = false) String motivo) {
        Exigir.establecimiento(sesion, motivo, establecimientoId);
        return servicios.operaciones.get()
                .uri(b -> b.path("/resumen")
                        .queryParam("establecimientoId", establecimientoId)
                        .queryParam("desde", desde)
                        .queryParam("hasta", hasta).build())
                .retrieve().body(Resumen.class);
    }

    @QueryMapping
    public List<Notificacion> notificaciones(@Argument UUID establecimientoId,
                                             @ContextValue(name = InterceptorSesion.SESION, required = false) Sesion sesion,
                                             @ContextValue(name = InterceptorSesion.SESION_ERROR, required = false) String motivo) {
        Exigir.establecimiento(sesion, motivo, establecimientoId);
        Notificacion[] r = servicios.relacion.get()
                .uri(b -> b.path("/notificaciones")
                        .queryParam("establecimientoId", establecimientoId).build())
                .retrieve().body(Notificacion[].class);
        return r == null ? List.of() : List.of(r);
    }

    /**
     * Cupos libres de un profesional en una fecha. Lo resuelve ms-agenda.
     *
     * GET /disponibilidad devuelve una lista directa de franjas, no un objeto
     * que las envuelva. Por eso se deserializa en Franja[] y no en un tipo
     * contenedor: si no coincide la forma, Jackson falla y GraphQL responde
     * INTERNAL_ERROR sin explicar la causa.
     *
     * servicioId no se reenvia porque el endpoint no lo recibe: en esta
     * entrega la jornada es fija en franjas de una hora y la duracion del
     * servicio todavia no recorta la franja.
     */
    @QueryMapping
    public List<Franja> disponibilidad(@Argument UUID profesionalId,
                                       @Argument UUID servicioId,
                                       @Argument String fecha) {
        Franja[] r = servicios.agenda.get()
                .uri(b -> b.path("/disponibilidad")
                        .queryParam("profesionalId", profesionalId)
                        .queryParam("fecha", fecha).build())
                .retrieve().body(Franja[].class);
        return r == null ? List.of() : List.of(r);
    }

    /** Clientes que no han vuelto. Lo resuelve ms-relacion-cliente. */
    @QueryMapping
    public List<Recurrencia> clientesInactivos(@Argument UUID establecimientoId,
                                               @ContextValue(name = InterceptorSesion.SESION, required = false) Sesion sesion,
                                               @ContextValue(name = InterceptorSesion.SESION_ERROR, required = false) String motivo) {
        Exigir.establecimiento(sesion, motivo, establecimientoId);
        Recurrencia[] r = servicios.relacion.get()
                .uri(b -> b.path("/clientes-inactivos")
                        .queryParam("establecimientoId", establecimientoId).build())
                .retrieve().body(Recurrencia[].class);
        return r == null ? List.of() : List.of(r);
    }

    // ---------- Mutaciones ----------

    /** Reservar desde el gateway. La decision la sigue tomando ms-agenda. */
    @MutationMapping
    public Cita reservarCita(@Argument UUID establecimientoId,
                             @Argument UUID profesionalId,
                             @Argument UUID servicioId,
                             @Argument UUID clienteId,
                             @Argument String inicio,
                             @Argument String fin) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("establecimientoId", establecimientoId);
        cuerpo.put("profesionalId", profesionalId);
        cuerpo.put("servicioId", servicioId);
        cuerpo.put("clienteId", clienteId);
        cuerpo.put("inicio", inicio);
        cuerpo.put("fin", fin);
        return servicios.agenda.post()
                .uri("/citas")
                .body(cuerpo)
                .retrieve().body(Cita.class);
    }

    /**
     * La clienta confirma que asistira. Es publica, igual que reservarCita:
     * no exige sesion. La decision (que la cita exista y siga agendada) la
     * sigue tomando ms-agenda.
     */
    @MutationMapping
    public Cita confirmarCita(@Argument UUID citaId) {
        return servicios.agenda.post()
                .uri("/citas/{id}/confirmacion", citaId)
                .retrieve().body(Cita.class);
    }

    @MutationMapping
    public Cita cerrarCita(@Argument UUID citaId, @Argument BigDecimal valorCobrado,
                           @ContextValue(name = InterceptorSesion.SESION, required = false) Sesion sesion,
                           @ContextValue(name = InterceptorSesion.SESION_ERROR, required = false) String motivo) {
        Exigir.sesion(sesion, motivo);
        return servicios.agenda.post()
                .uri("/citas/{id}/cierre", citaId)
                .body(Map.of("valorCobrado", valorCobrado))
                .retrieve().body(Cita.class);
    }

    @MutationMapping
    public Cita cancelarCita(@Argument UUID citaId,
                             @Argument String canceladaPor,
                             @Argument String motivo,
                             @ContextValue(name = InterceptorSesion.SESION, required = false) Sesion sesion,
                             @ContextValue(name = InterceptorSesion.SESION_ERROR, required = false) String motivoSesion) {
        // El cliente anula sus propias citas sin sesion; cualquier otro origen
        // (el panel del establecimiento) debe haber iniciado sesion.
        if (!"cliente".equals(canceladaPor)) {
            Exigir.sesion(sesion, motivoSesion);
        }
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canceladaPor", canceladaPor);
        cuerpo.put("motivo", motivo == null ? "" : motivo);
        return servicios.agenda.post()
                .uri("/citas/{id}/cancelacion", citaId)
                .body(cuerpo)
                .retrieve().body(Cita.class);
    }

    private Servicio buscarServicio(UUID servicioId) {
        try {
            return servicios.establecimientos.get()
                    .uri("/servicios/{id}", servicioId)
                    .retrieve().body(Servicio.class);
        } catch (Exception e) {
            return null;
        }
    }
}
