package com.fet.citas.relacion.dominio;

import com.fet.citas.relacion.eventos.EventoCita;
import com.fet.citas.relacion.eventos.EventoInsumoBajo;
import com.fet.citas.relacion.persistencia.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Todo lo que mira hacia el cliente: cuando contactarlo y por que.
 *
 * Reacciona a cuatro topics distintos. Ninguna de estas acciones hace
 * esperar al profesional: todas ocurren despues de que la cita ya quedo
 * registrada.
 */
@Service
public class RelacionService {

    private static final Logger log = LoggerFactory.getLogger(RelacionService.class);

    /** Zona horaria de Colombia, la misma que usa ms-agenda para su jornada. */
    private static final ZoneOffset COLOMBIA = ZoneOffset.of("-05:00");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificacionRepository notificaciones;
    private final ClienteRecurrenciaRepository recurrencias;
    private final int periodicidadDias;

    public RelacionService(NotificacionRepository notificaciones,
                           ClienteRecurrenciaRepository recurrencias,
                           @Value("${app.recurrencia.dias:28}") int periodicidadDias) {
        this.notificaciones = notificaciones;
        this.recurrencias = recurrencias;
        this.periodicidadDias = periodicidadDias;
    }

    /** citas.agendada -> confirmar al cliente y avisar al profesional. */
    @Transactional
    public void citaAgendada(EventoCita e) {
        registrar(e.establecimientoId(), e.citaId(), "confirmacion-reserva",
                "Tu cita quedo reservada",
                "Cita del " + e.inicio() + ". Te recordaremos antes de la fecha.");
    }

    /** citas.cancelada -> avisar y anotar la cancelacion en el historial. */
    @Transactional
    public void citaCancelada(EventoCita e) {
        registrar(e.establecimientoId(), e.citaId(), "aviso-cancelacion",
                "Tu cita fue cancelada",
                "Cita del " + e.inicio() + " cancelada."
                        + (e.motivo() == null ? "" : " Motivo: " + e.motivo()));
        historial(e.establecimientoId(), e.clienteId()).registrarCancelacion();
    }

    /**
     * citas.completada -> solicitar valoracion y programar el recontacto.
     *
     * Son los dos procesos de este servicio que dispara el mismo evento que
     * ms-operaciones usa para liquidar. Ninguno de los dos servicios sabe
     * que el otro existe.
     */
    @Transactional
    public void citaCompletada(EventoCita e) {
        registrar(e.establecimientoId(), e.citaId(), "solicitud-valoracion",
                "Como te fue?",
                "Cuentanos como estuvo tu servicio del " + e.inicio() + ".");

        ClienteRecurrencia historial = historial(e.establecimientoId(), e.clienteId());
        historial.registrarVisita(e.inicio(), periodicidadDias);
        recurrencias.save(historial);
        log.info("Recurrencia actualizada: cliente {} vuelve a contactarse el {}",
                e.clienteId(), historial.getProximoContacto());
    }

    /** inventario.insumo-bajo -> avisar al profesional que debe reponer. */
    @Transactional
    public void insumoBajo(EventoInsumoBajo e) {
        notificaciones.save(new Notificacion(
                e.establecimientoId(), null, "alerta-insumo",
                "Queda poco " + e.nombre(),
                "Existencia " + e.existencia() + ", umbral " + e.umbralMinimo() + "."));
        log.info("Alerta de reposicion registrada para {}", e.nombre());
    }

    /**
     * Recordatorio de una cita proxima. A diferencia de las demas
     * notificaciones, no nace de un evento de Kafka: lo pide el
     * GeneradorRecordatorios cuando la cita entra en la ventana de
     * confirmacion de su establecimiento.
     *
     * Reutiliza registrar(), asi que es idempotente igual que el resto: si
     * ya hay un recordatorio de esa cita no se crea otro (UNIQUE cita_id,tipo).
     * La hora se escribe en Colombia (-05:00), la misma zona que usa
     * ms-agenda; el texto no depende de la zona del servidor.
     *
     * @return true si se registro; false si ya existia
     */
    @Transactional
    public boolean recordatorio(UUID establecimientoId, UUID citaId, OffsetDateTime inicio) {
        String fecha = inicio.withOffsetSameInstant(COLOMBIA).format(FORMATO_FECHA);
        return registrar(establecimientoId, citaId, "recordatorio-cita",
                "Recuerda tu cita",
                "Tu cita es el " + fecha + " (hora de Colombia). "
                        + "Confirmala en Mis citas para asegurar tu cupo.");
    }

    // --- consultas ---

    @Transactional(readOnly = true)
    public List<Notificacion> notificaciones(UUID establecimientoId) {
        return notificaciones.findByEstablecimientoIdOrderByCreadaEnDesc(establecimientoId);
    }

    @Transactional(readOnly = true)
    public ClienteRecurrencia recurrencia(UUID establecimientoId, UUID clienteId) {
        return recurrencias.findByEstablecimientoIdAndClienteId(establecimientoId, clienteId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ClienteRecurrencia> inactivos(UUID establecimientoId) {
        return recurrencias.findInactivos(establecimientoId, OffsetDateTime.now());
    }

    // --- apoyo ---

    /**
     * Guarda la notificacion solo si no existia ya para esa cita y ese tipo.
     * Es el control de idempotencia: Kafka entrega al menos una vez, asi
     * que el mismo evento puede llegar dos veces (RNF-04).
     */
    private boolean registrar(UUID establecimientoId, UUID citaId, String tipo,
                              String asunto, String cuerpo) {
        if (citaId != null && notificaciones.existsByCitaIdAndTipo(citaId, tipo)) {
            log.info("Notificacion {} de la cita {} ya existia, se ignora", tipo, citaId);
            return false;
        }
        notificaciones.save(new Notificacion(establecimientoId, citaId, tipo, asunto, cuerpo));
        log.info("Notificacion registrada: {} para la cita {}", tipo, citaId);
        return true;
    }

    /** Busca el historial del cliente o lo crea si es su primera vez. */
    private ClienteRecurrencia historial(UUID establecimientoId, UUID clienteId) {
        return recurrencias.findByEstablecimientoIdAndClienteId(establecimientoId, clienteId)
                .orElseGet(() -> recurrencias.save(
                        new ClienteRecurrencia(establecimientoId, clienteId)));
    }
}
