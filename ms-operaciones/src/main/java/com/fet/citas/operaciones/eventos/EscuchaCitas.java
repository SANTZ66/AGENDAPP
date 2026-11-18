package com.fet.citas.operaciones.eventos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fet.citas.operaciones.dominio.OperacionesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * El consumidor. Escucha citas.completada y dispara los dos procesos
 * posteriores de este servicio.
 *
 * El grupo de consumo es "operaciones": propio de este servicio. Los otros
 * consumidores del mismo topic tienen su propio grupo, asi que todos
 * reciben una copia del evento y ninguno sabe que los demas existen.
 *
 * Fijate en que este metodo NO atrapa las excepciones. Es deliberado: si
 * algo falla, la excepcion sube hasta el DefaultErrorHandler configurado en
 * ManejoDeErroresKafka, que reintenta y, si no lo logra, deja el mensaje en
 * el topic .DLT. Atraparla aqui haria que el evento se diera por procesado
 * y se perdiera para siempre.
 */
@Component
public class EscuchaCitas {

    private static final Logger log = LoggerFactory.getLogger(EscuchaCitas.class);

    private final OperacionesService service;
    private final ObjectMapper json;

    public EscuchaCitas(OperacionesService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = "citas.completada", groupId = "operaciones")
    public void citaCompletada(String mensaje) {
        EventoCita evento = deserializar(mensaje);
        log.info("Recibido citas.completada -> cita {}", evento.citaId());
        service.procesarCitaCompletada(evento);
    }

    /**
     * Un JSON malformado es un error NO recuperable: el intento numero tres
     * fallara igual que el primero. Se convierte en IllegalArgumentException
     * para que el manejador lo mande al DLT sin gastar reintentos.
     */
    private EventoCita deserializar(String mensaje) {
        try {
            return json.readValue(mensaje, EventoCita.class);
        } catch (JsonProcessingException e) {
            log.error("Evento con formato invalido, va directo al DLT: {}", mensaje);
            throw new IllegalArgumentException("Evento con formato invalido", e);
        }
    }
}
