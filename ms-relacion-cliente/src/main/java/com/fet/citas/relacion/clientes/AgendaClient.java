package com.fet.citas.relacion.clientes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Llamada REST a ms-agenda para saber que citas estan por llegar.
 *
 * Este servicio solo conoce las citas por los eventos de Kafka, y un evento
 * no avisa de que "ya casi es la hora". Para eso hay que preguntar: es
 * comunicacion SINCRONA, igual que la de ms-operaciones con el catalogo.
 *
 * Si ms-agenda no responde, el resultado es una lista vacia y se registra un
 * aviso: quien llama simplemente vuelve a intentarlo en su siguiente vuelta.
 */
@Component
public class AgendaClient {

    private static final Logger log = LoggerFactory.getLogger(AgendaClient.class);

    /** Citas por pagina que se piden a ms-agenda. */
    static final int TAMANO_PAGINA = 100;
    /** Tope de paginas por consulta, para no girar sin fin ante una respuesta rara. */
    static final int MAXIMO_PAGINAS = 50;

    private final RestClient rest;

    public AgendaClient(@Value("${app.agenda.url}") String baseUrl) {
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Citas AGENDADAS de cualquier profesional que empiezan entre desde y
     * hasta (extremos incluidos), de la mas cercana a la mas lejana. Recorre
     * todas las paginas.
     *
     * Las fechas se envian en UTC (terminadas en Z): un "+" en un offset
     * como +00:00 se pierde en la URL.
     */
    public List<CitaProxima> proximas(Instant desde, Instant hasta) {
        String d = desde.truncatedTo(ChronoUnit.SECONDS).toString();
        String h = hasta.truncatedTo(ChronoUnit.SECONDS).toString();
        List<CitaProxima> todas = new ArrayList<>();
        try {
            for (int pagina = 0; pagina < MAXIMO_PAGINAS; pagina++) {
                final int numero = pagina;
                Pagina p = rest.get()
                        .uri(b -> b.path("/citas/proximas")
                                .queryParam("desde", d)
                                .queryParam("hasta", h)
                                .queryParam("page", numero)
                                .queryParam("size", TAMANO_PAGINA)
                                .build())
                        .retrieve()
                        .body(Pagina.class);
                if (p == null || p.content() == null) {
                    break;
                }
                todas.addAll(p.content());
                if (p.last()) {
                    return todas;
                }
            }
            log.warn("Se alcanzo el tope de {} paginas de citas proximas", MAXIMO_PAGINAS);
            return todas;
        } catch (Exception e) {
            log.warn("No se pudieron consultar las citas proximas: {}", e.getMessage());
            return List.of();
        }
    }

    /** Lo que se usa de cada cita de GET /citas/proximas. */
    public record CitaProxima(UUID id, UUID establecimientoId,
                              OffsetDateTime inicio, OffsetDateTime confirmadaEn) {
    }

    /** Una pagina de Spring Data, tal como la devuelve ms-agenda. */
    record Pagina(List<CitaProxima> content, boolean last) {
    }
}
