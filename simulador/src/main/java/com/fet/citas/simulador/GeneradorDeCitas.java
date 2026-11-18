package com.fet.citas.simulador;

import com.fet.citas.simulador.clientes.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Genera una cita cada cierto tiempo y decide que hacer con ella.
 *
 * El reparto esta pensado para que los TRES topics de citas reciban
 * mensajes y se vea el flujo completo en Kafka UI:
 *
 *   60% se cierra    -> citas.agendada + citas.completada
 *   20% se cancela   -> citas.agendada + citas.cancelada
 *   20% se deja viva -> citas.agendada
 *
 * Se apaga con app.simulador.activo=false.
 */
@Component
@ConditionalOnProperty(name = "app.simulador.activo", havingValue = "true")
public class GeneradorDeCitas {

    private static final Logger log = LoggerFactory.getLogger(GeneradorDeCitas.class);
    private static final ZoneOffset COLOMBIA = ZoneOffset.of("-05:00");

    private final ApiClient api;
    private final Escenario escenario;

    /** Cada cita toma una franja distinta: evita chocar con la restriccion
     *  UNIQUE (profesional_id, inicio) y recibir un 409. */
    private final AtomicInteger franja = new AtomicInteger(0);
    private final AtomicInteger generadas = new AtomicInteger(0);

    public GeneradorDeCitas(ApiClient api, Escenario escenario) {
        this.api = api;
        this.escenario = escenario;
    }

    @Scheduled(fixedDelayString = "${app.simulador.intervalo-ms:15000}",
               initialDelayString = "${app.simulador.espera-inicial-ms:30000}")
    public void generar() {
        if (!escenario.estaListo()) {
            log.debug("El escenario aun no esta listo");
            return;
        }
        try {
            UUID citaId = reservar();
            if (citaId == null) {
                return;
            }
            int suerte = ThreadLocalRandom.current().nextInt(100);
            if (suerte < 60) {
                cerrar(citaId);
            } else if (suerte < 80) {
                cancelar(citaId);
            } else {
                log.info("Cita {} queda agendada", citaId);
            }
            log.info("Citas generadas hasta ahora: {}", generadas.incrementAndGet());
        } catch (Exception e) {
            log.warn("Fallo al generar actividad: {}", e.getMessage());
        }
    }

    private UUID reservar() {
        OffsetDateTime inicio = OffsetDateTime.now(COLOMBIA)
                .plusDays(1)
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(franja.getAndIncrement());

        Map<String, Object> cuerpo = Map.of(
                "establecimientoId", escenario.establecimientoId(),
                "profesionalId", escenario.profesionalId(),
                "servicioId", escenario.servicioId(),
                "clienteId", clienteAlAzar(),
                "inicio", inicio.toString(),
                "fin", inicio.plusHours(1).toString());

        @SuppressWarnings("rawtypes")
        Map respuesta = api.agenda.post().uri("/citas")
                .body(cuerpo).retrieve().body(Map.class);

        if (respuesta == null || respuesta.get("id") == null) {
            return null;
        }
        UUID id = UUID.fromString(String.valueOf(respuesta.get("id")));
        log.info("Cita {} reservada para {}", id, inicio);
        return id;
    }

    private void cerrar(UUID citaId) {
        api.agenda.post().uri("/citas/{id}/cierre", citaId)
                .body(Map.of("valorCobrado", 45000))
                .retrieve().toBodilessEntity();
        log.info("Cita {} cerrada", citaId);
    }

    private void cancelar(UUID citaId) {
        api.agenda.post().uri("/citas/{id}/cancelacion", citaId)
                .body(Map.of("canceladaPor", "cliente", "motivo", "generada por el simulador"))
                .retrieve().toBodilessEntity();
        log.info("Cita {} cancelada", citaId);
    }

    /**
     * Pocos clientes distintos, a proposito: asi se acumulan visitas sobre
     * los mismos y se puede ver la recurrencia en ms-relacion-cliente.
     */
    private UUID clienteAlAzar() {
        int n = ThreadLocalRandom.current().nextInt(1, 6);
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + n);
    }

    public int generadas() {
        return generadas.get();
    }
}
