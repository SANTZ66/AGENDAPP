package com.fet.citas.agenda.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica en Kafka los hechos de negocio de la agenda.
 *
 * El @TransactionalEventListener con AFTER_COMMIT es la pieza clave: este
 * metodo solo se ejecuta cuando la transaccion de base de datos ya
 * confirmo. Si la reserva falla, nunca se publica nada.
 *
 * La clave de particion es el establecimiento. Asi los eventos de un mismo
 * establecimiento se procesan en orden, y la carga se reparte entre
 * particiones cuando crezca el numero de establecimientos.
 *
 * Se desactiva en las pruebas con app.eventos.habilitados=false, para que
 * los tests no necesiten un Kafka levantado.
 */
@Component
@ConditionalOnProperty(name = "app.eventos.habilitados",
                       havingValue = "true", matchIfMissing = true)
public class PublicadorKafka {

    private static final Logger log = LoggerFactory.getLogger(PublicadorKafka.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public PublicadorKafka(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(HechoDeCita hecho) {
        try {
            String cuerpo = json.writeValueAsString(hecho.evento());
            String clave = hecho.evento().establecimientoId().toString();
            kafka.send(hecho.topic(), clave, cuerpo);
            log.info("Evento publicado en {} -> cita {}",
                    hecho.topic(), hecho.evento().citaId());
        } catch (Exception e) {
            // La cita YA quedo guardada. Un fallo al publicar no debe
            // deshacerla: se registra para poder reprocesarlo despues.
            log.error("No se pudo publicar el evento en {}: {}",
                    hecho.topic(), e.getMessage());
        }
    }
}
