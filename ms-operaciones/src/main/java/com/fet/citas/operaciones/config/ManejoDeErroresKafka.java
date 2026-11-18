package com.fet.citas.operaciones.config;

import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Que hacer cuando un consumidor falla al procesar un evento.
 *
 * Sin esta configuracion, Spring reintenta diez veces seguidas y despues
 * descarta el mensaje: el evento se pierde y nadie se entera. Eso rompe la
 * promesa del RNF-02 del documento, que dice que un evento no procesado
 * debe poder consumirse una vez restablecido el servicio.
 *
 * Con esta configuracion:
 *
 *   1. Si el procesamiento falla, se reintenta 3 veces esperando 2 segundos
 *      entre intentos. Cubre el caso tipico: la base de datos o el servicio
 *      de catalogo estaban caidos un momento.
 *
 *   2. Si despues de los reintentos sigue fallando, el mensaje NO se
 *      descarta: se publica en un topic paralelo llamado igual con el
 *      sufijo .DLT (Dead Letter Topic). Queda integro, con su clave y su
 *      contenido, para poder revisarlo o reprocesarlo.
 *
 *   3. Hay errores que no tiene sentido reintentar. Si el JSON viene
 *      malformado, el intento numero diez fallara igual que el primero.
 *      Esos van directo al DLT sin gastar reintentos.
 */
@Configuration
public class ManejoDeErroresKafka {

    private static final Logger log = LoggerFactory.getLogger(ManejoDeErroresKafka.class);

    private static final long ESPERA_ENTRE_INTENTOS_MS = 2000L;
    private static final long REINTENTOS = 3L;

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {

        // A donde va el mensaje cuando se agotan los reintentos: al mismo
        // topic con el sufijo .DLT y conservando la particion original.
        DeadLetterPublishingRecoverer alDlt = new DeadLetterPublishingRecoverer(kafka);

        DefaultErrorHandler manejador =
                new DefaultErrorHandler(alDlt, new FixedBackOff(ESPERA_ENTRE_INTENTOS_MS, REINTENTOS));

        // Errores no recuperables: reintentarlos no cambia nada.
        manejador.addNotRetryableExceptions(
                SerializationException.class,
                IllegalArgumentException.class);

        manejador.setRetryListeners((registro, excepcion, intento) ->
                log.warn("Intento {} fallido en {} particion {} offset {}: {}",
                        intento, registro.topic(), registro.partition(),
                        registro.offset(), excepcion.getMessage()));

        return manejador;
    }
}
