package com.fet.citas.agenda.dominio;

import com.fet.citas.agenda.dto.*;
import com.fet.citas.agenda.error.CitaNoEncontradaException;
import com.fet.citas.agenda.error.CupoOcupadoException;
import com.fet.citas.agenda.error.EstadoInvalidoException;
import com.fet.citas.agenda.eventos.EventoCita;
import com.fet.citas.agenda.eventos.HechoDeCita;
import com.fet.citas.agenda.persistencia.CitaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reglas de negocio de la agenda. El controlador (capa REST) delega aqui
 * toda la logica; asi la logica queda separada del transporte HTTP y se
 * puede probar de forma aislada (requisito "organizacion coherente de
 * responsabilidades").
 */
@Service
public class CitaService {

    private final CitaRepository repo;
    private final ApplicationEventPublisher eventos;

    public CitaService(CitaRepository repo, ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.eventos = eventos;
    }

    /**
     * Reserva una cita. Operacion transaccional: o queda guardada por
     * completo o no queda nada (RNF-06).
     *
     * Se comprueba primero si el cupo esta libre para dar un 409 claro;
     * si aun asi dos reservas simultaneas pasan la comprobacion, la
     * restriccion UNIQUE de la BD garantiza que solo una gane (la otra
     * cae en el manejador de DataIntegrityViolationException).
     */
    @Transactional
    public Cita reservar(ReservarCitaRequest req) {
        if (repo.existsByProfesionalIdAndInicio(req.profesionalId(), req.inicio())) {
            throw new CupoOcupadoException(
                    "El profesional ya tiene una cita a las " + req.inicio());
        }
        Cita cita = new Cita(
                req.establecimientoId(),
                req.profesionalId(),
                req.servicioId(),
                req.clienteId(),
                req.inicio(),
                req.fin());
        Cita guardada = repo.save(cita);
        publicar(HechoDeCita.AGENDADA, guardada, null);
        return guardada;
    }

    /** Agenda de un profesional en un rango de fechas, paginada. */
    @Transactional(readOnly = true)
    public Page<Cita> agenda(UUID profesionalId, OffsetDateTime desde,
                             OffsetDateTime hasta, Pageable pageable) {
        return repo.findByProfesionalIdAndInicioBetween(
                profesionalId, desde, hasta, pageable);
    }

    /** Cierra una cita tras prestar el servicio. Debe estar agendada. */
    @Transactional
    public Cita cerrar(UUID citaId, CerrarCitaRequest req) {
        Cita cita = buscar(citaId);
        if (cita.getEstado() != EstadoCita.AGENDADA) {
            throw new EstadoInvalidoException(
                    "Solo se puede cerrar una cita agendada; esta esta "
                            + cita.getEstado().name().toLowerCase());
        }
        cita.cerrar(req.valorCobrado());
        Cita guardada = repo.save(cita);
        publicar(HechoDeCita.COMPLETADA, guardada, null);
        return guardada;
    }

    /**
     * La clienta confirma que asistira. Debe estar agendada.
     *
     * Es idempotente: confirmar dos veces devuelve la misma cita con la
     * fecha de la primera confirmacion. NO publica ningun evento: la
     * confirmacion no cambia el estado de la cita ni es un hecho que los
     * demas servicios necesiten por el bus.
     */
    @Transactional
    public Cita confirmar(UUID citaId) {
        Cita cita = buscar(citaId);
        if (cita.getEstado() != EstadoCita.AGENDADA) {
            throw new EstadoInvalidoException(
                    "Solo se puede confirmar una cita agendada; esta esta "
                            + cita.getEstado().name().toLowerCase());
        }
        cita.confirmar();
        return repo.save(cita);
    }

    /**
     * Citas agendadas que empiezan dentro de un rango, de cualquier
     * profesional. A diferencia de agenda(), no exige un profesional.
     */
    @Transactional(readOnly = true)
    public Page<Cita> proximas(OffsetDateTime desde, OffsetDateTime hasta, Pageable pageable) {
        return repo.findByEstadoAndInicioBetween(
                EstadoCita.AGENDADA, desde, hasta, pageable);
    }

    /** Cancela una cita y libera el cupo. Debe estar agendada. */
    @Transactional
    public Cita cancelar(UUID citaId, CancelarCitaRequest req) {
        Cita cita = buscar(citaId);
        if (cita.getEstado() != EstadoCita.AGENDADA) {
            throw new EstadoInvalidoException(
                    "Solo se puede cancelar una cita agendada; esta esta "
                            + cita.getEstado().name().toLowerCase());
        }
        cita.cancelar(req.canceladaPor(), req.motivo());
        Cita guardada = repo.save(cita);
        publicar(HechoDeCita.CANCELADA, guardada, req.motivo());
        return guardada;
    }

    /**
     * Franjas libres de un profesional en una fecha. Jornada fija de 09:00
     * a 18:00 en bloques de una hora; se descartan las horas ya ocupadas
     * por una cita activa.
     */
    @Transactional(readOnly = true)
    public java.util.List<com.fet.citas.agenda.dto.FranjaDisponible> franjasLibres(
            UUID profesionalId, java.time.LocalDate fecha) {

        var franjas = new java.util.ArrayList<com.fet.citas.agenda.dto.FranjaDisponible>();
        // Zona horaria de Colombia (UTC-5) para construir las horas del dia.
        var zona = java.time.ZoneOffset.of("-05:00");

        for (int hora = 9; hora < 18; hora++) {
            OffsetDateTime inicio = fecha.atTime(hora, 0).atOffset(zona);
            OffsetDateTime fin = inicio.plusHours(1);
            if (!repo.existsByProfesionalIdAndInicio(profesionalId, inicio)) {
                franjas.add(new com.fet.citas.agenda.dto.FranjaDisponible(inicio, fin));
            }
        }
        return franjas;
    }

    /**
     * Anuncia el hecho. No lo manda a Kafka todavia: lo entrega al
     * publicador de Spring, que lo enviara cuando la transaccion confirme.
     */
    private void publicar(String topic, Cita cita, String motivo) {
        eventos.publishEvent(new HechoDeCita(topic, EventoCita.desde(cita, motivo)));
    }

    private Cita buscar(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new CitaNoEncontradaException(id));
    }
}
