package com.fet.citas.operaciones;

import com.fet.citas.operaciones.persistencia.LiquidacionRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pruebas del flujo asincrono de ms-operaciones.
 *
 * Usan un Kafka REAL, pero embebido: @EmbeddedKafka arranca un broker
 * dentro de la misma JVM del test. No hace falta Docker ni que el
 * docker compose este levantado.
 *
 * Lo que se verifica es lo que pide la actividad:
 *
 *   1. Consumidor funcional e integrado: se publica un evento de verdad en
 *      el topic y se comprueba que el consumidor lo proceso y que eso
 *      cambio la base de datos.
 *
 *   2. Idempotencia: el mismo evento entregado dos veces no duplica el
 *      ingreso. Kafka garantiza entrega "al menos una vez", asi que esto
 *      no es opcional.
 *
 *   3. Manejo de errores: un evento con JSON malformado no tumba al
 *      consumidor ni se pierde, sino que acaba en el topic .DLT.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1,
        topics = {"citas.completada", "citas.completada.DLT", "inventario.insumo-bajo"})
class FlujoEventosTest {

    private static final String TOPIC = "citas.completada";
    private static final String TOPIC_DLT = "citas.completada.DLT";

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired LiquidacionRepository liquidaciones;
    @Autowired EmbeddedKafkaBroker broker;

    @BeforeEach
    void limpiar() {
        liquidaciones.deleteAll();
    }

    @Test
    @DisplayName("Un evento citas.completada queda liquidado en la base de datos")
    void procesaElEventoYPersiste() {
        UUID citaId = UUID.randomUUID();
        UUID establecimientoId = UUID.randomUUID();

        kafka.send(TOPIC, establecimientoId.toString(),
                eventoJson(citaId, establecimientoId, 45000));

        // El consumo es asincrono: hay que esperar a que ocurra, no asumirlo.
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> liquidaciones.existsByCitaId(citaId));

        var liquidacion = liquidaciones.findAll().stream()
                .filter(l -> l.getCitaId().equals(citaId))
                .findFirst()
                .orElseThrow();

        assertThat(liquidacion.getValor().intValue()).isEqualTo(45000);
        assertThat(liquidacion.getEstablecimientoId()).isEqualTo(establecimientoId);
    }

    @Test
    @DisplayName("El mismo evento entregado dos veces no duplica el ingreso")
    void esIdempotente() throws InterruptedException {
        UUID citaId = UUID.randomUUID();
        UUID establecimientoId = UUID.randomUUID();
        String evento = eventoJson(citaId, establecimientoId, 45000);

        kafka.send(TOPIC, establecimientoId.toString(), evento);
        kafka.send(TOPIC, establecimientoId.toString(), evento);

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> liquidaciones.existsByCitaId(citaId));

        // Dar margen por si la segunda entrega llega con retraso.
        Thread.sleep(3000);

        long cuantas = liquidaciones.findAll().stream()
                .filter(l -> l.getCitaId().equals(citaId))
                .count();

        assertThat(cuantas)
                .as("una sola liquidacion aunque el evento llegue dos veces")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un evento con JSON invalido termina en el topic .DLT y no se pierde")
    void eventoInvalidoVaAlDlt() {
        Consumer<String, String> consumidorDlt = consumidorDe(TOPIC_DLT);

        kafka.send(TOPIC, "clave-cualquiera", "{ esto no es json valido ");

        ConsumerRecord<String, String> enElDlt =
                KafkaTestUtils.getSingleRecord(consumidorDlt, TOPIC_DLT, Duration.ofSeconds(30));

        assertThat(enElDlt).isNotNull();
        assertThat(enElDlt.value())
                .as("el mensaje llega intacto al DLT, no se descarta")
                .contains("esto no es json valido");

        consumidorDlt.close();
    }

    // --- apoyo ---

    /** Un consumidor de prueba para leer lo que llega a un topic. */
    private Consumer<String, String> consumidorDe(String topic) {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("grupo-de-prueba-" + topic, "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumidor = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumidor, topic);
        return consumidor;
    }

    /** El mismo JSON que publica ms-agenda. */
    private String eventoJson(UUID citaId, UUID establecimientoId, int valor) {
        return """
                {
                  "citaId": "%s",
                  "establecimientoId": "%s",
                  "profesionalId": "%s",
                  "servicioId": "%s",
                  "clienteId": "%s",
                  "inicio": "2026-11-10T09:00:00-05:00",
                  "fin": "2026-11-10T10:00:00-05:00",
                  "estado": "completada",
                  "valorCobrado": %d,
                  "motivo": null,
                  "ocurridoEn": "2026-11-10T10:05:00-05:00"
                }
                """.formatted(citaId, establecimientoId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), valor);
    }
}
