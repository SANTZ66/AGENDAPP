package com.fet.citas.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fet.citas.agenda.persistencia.CitaRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba del PRODUCTOR.
 *
 * Comprueba lo que el documento promete y que a simple vista no se ve: que
 * al reservar una cita se publica un evento en citas.agendada, y que ese
 * evento se publica DESPUES de que la transaccion confirmo.
 *
 * Usa un Kafka real embebido en la misma JVM, sin Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"citas.agendada"})
@TestPropertySource(properties = {
        "app.eventos.habilitados=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class PublicacionEventosTest {

    private static final String TOPIC = "citas.agendada";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CitaRepository repo;
    @Autowired EmbeddedKafkaBroker broker;

    private Consumer<String, String> consumidor;

    @BeforeEach
    void preparar() {
        repo.deleteAll();
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("grupo-de-prueba-agendada", "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumidor = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumidor, TOPIC);

        // Los dos tests comparten el mismo topic embebido. consumeFromAnEmbeddedTopic
        // deja al consumidor al PRINCIPIO del topic, asi que sin esto cada test
        // leia tambien los eventos del otro y el resultado dependia del orden.
        // Se mueve al final: cada test solo ve lo que publica el mismo.
        // position() fuerza a que el seekToEnd se aplique ya, no en el primer poll.
        consumidor.seekToEnd(consumidor.assignment());
        consumidor.assignment().forEach(consumidor::position);
    }

    @AfterEach
    void cerrar() {
        if (consumidor != null) {
            consumidor.close();
        }
    }

    @Test
    @DisplayName("Reservar una cita publica el evento citas.agendada")
    void reservarPublicaElEvento() throws Exception {
        UUID establecimientoId = UUID.randomUUID();
        UUID profesionalId = UUID.randomUUID();

        String cuerpo = json.writeValueAsString(Map.of(
                "establecimientoId", establecimientoId,
                "profesionalId", profesionalId,
                "servicioId", UUID.randomUUID(),
                "clienteId", UUID.randomUUID(),
                "inicio", "2026-11-10T09:00:00-05:00",
                "fin", "2026-11-10T10:00:00-05:00"));

        mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());

        ConsumerRecord<String, String> evento =
                KafkaTestUtils.getSingleRecord(consumidor, TOPIC, Duration.ofSeconds(20));

        assertThat(evento).as("se publico un evento en " + TOPIC).isNotNull();

        // La clave de particion es el establecimiento: garantiza que los
        // eventos de un mismo negocio se procesen en orden.
        assertThat(evento.key()).isEqualTo(establecimientoId.toString());

        assertThat(evento.value())
                .contains(establecimientoId.toString())
                .contains(profesionalId.toString())
                .contains("\"estado\":\"agendada\"");
    }

    @Test
    @DisplayName("Una reserva rechazada por cupo ocupado no publica ningun evento")
    void reservaRechazadaNoPublica() throws Exception {
        UUID establecimientoId = UUID.randomUUID();
        UUID profesionalId = UUID.randomUUID();

        String cuerpo = json.writeValueAsString(Map.of(
                "establecimientoId", establecimientoId,
                "profesionalId", profesionalId,
                "servicioId", UUID.randomUUID(),
                "clienteId", UUID.randomUUID(),
                "inicio", "2026-11-12T09:00:00-05:00",
                "fin", "2026-11-12T10:00:00-05:00"));

        // La primera gana el cupo.
        mvc.perform(post("/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        // La segunda pierde la carrera y recibe 409.
        mvc.perform(post("/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict());

        // Debe haber UN solo evento, no dos: el rechazado no publica nada
        // porque la transaccion nunca confirmo.
        var registros = KafkaTestUtils.getRecords(consumidor, Duration.ofSeconds(10));
        assertThat(registros.records(TOPIC))
                .as("solo la reserva que prospero genera evento")
                .hasSize(1);
    }
}
