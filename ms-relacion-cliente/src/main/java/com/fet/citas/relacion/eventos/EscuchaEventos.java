package com.fet.citas.relacion.eventos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fet.citas.relacion.dominio.RelacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Los consumidores de este servicio. Escucha cuatro topics con el grupo
 * "relacion-cliente", independiente del grupo de ms-operaciones: los dos
 * reciben una copia de citas.completada y ninguno bloquea al otro.
 *
 * Ninguno de estos metodos atrapa excepciones. Si algo falla, el
 * DefaultErrorHandler de ManejoDeErroresKafka reintenta y, si no lo logra,
 * manda el mensaje al topic .DLT en vez de descartarlo.
 */
@Component
public class EscuchaEventos {

    private static final Logger log = LoggerFactory.getLogger(EscuchaEventos.class);

    private final RelacionService service;
    private final ObjectMapper json;

    public EscuchaEventos(RelacionService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = "citas.agendada", groupId = "relacion-cliente")
    public void agendada(String mensaje) {
        procesarCita(mensaje, "citas.agendada", service::citaAgendada);
    }

    @KafkaListener(topics = "citas.cancelada", groupId = "relacion-cliente")
    public void cancelada(String mensaje) {
        procesarCita(mensaje, "citas.cancelada", service::citaCancelada);
    }

    @KafkaListener(topics = "citas.completada", groupId = "relacion-cliente")
    public void completada(String mensaje) {
        procesarCita(mensaje, "citas.completada", service::citaCompletada);
    }

    @KafkaListener(topics = "inventario.insumo-bajo", groupId = "relacion-cliente")
    public void insumoBajo(String mensaje) {
        EventoInsumoBajo evento = deserializar(mensaje, EventoInsumoBajo.class);
        log.info("Recibido inventario.insumo-bajo -> {}", evento.nombre());
        service.insumoBajo(evento);
    }

    /** Lo comun a los tres topics de citas: deserializar y delegar. */
    private void procesarCita(String mensaje, String topic, Consumer<EventoCita> accion) {
        EventoCita evento = deserializar(mensaje, EventoCita.class);
        log.info("Recibido {} -> cita {}", topic, evento.citaId());
        accion.accept(evento);
    }

    /**
     * Un JSON malformado es un error NO recuperable. Se convierte en
     * IllegalArgumentException para que el manejador lo mande al DLT sin
     * gastar reintentos.
     */
    private <T> T deserializar(String mensaje, Class<T> tipo) {
        try {
            return json.readValue(mensaje, tipo);
        } catch (JsonProcessingException e) {
            log.error("Evento con formato invalido, va directo al DLT: {}", mensaje);
            throw new IllegalArgumentException("Evento con formato invalido", e);
        }
    }
}
