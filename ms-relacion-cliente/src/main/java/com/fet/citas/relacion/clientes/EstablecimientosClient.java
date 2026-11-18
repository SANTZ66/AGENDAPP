package com.fet.citas.relacion.clientes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Llamada REST a ms-establecimientos para conocer la ventana de confirmacion
 * de cada establecimiento: las horas que tiene la clienta para confirmar su
 * cita. Ese dato vive solo alli; este servicio no lo guarda.
 *
 * Es comunicacion SINCRONA, igual que la de ms-operaciones con el catalogo.
 * Si ms-establecimientos no responde, el resultado es una lista vacia y se
 * registra un aviso.
 */
@Component
public class EstablecimientosClient {

    private static final Logger log = LoggerFactory.getLogger(EstablecimientosClient.class);

    private final RestClient rest;

    public EstablecimientosClient(@Value("${app.establecimientos.url}") String baseUrl) {
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Todos los establecimientos con su ventana de confirmacion en horas. */
    public List<Establecimiento> listar() {
        try {
            Establecimiento[] lista = rest.get()
                    .uri("/establecimientos")
                    .retrieve()
                    .body(Establecimiento[].class);
            return lista == null ? List.of() : List.of(lista);
        } catch (Exception e) {
            log.warn("No se pudo consultar la lista de establecimientos: {}", e.getMessage());
            return List.of();
        }
    }

    /** Lo que se usa de cada establecimiento de GET /establecimientos. */
    public record Establecimiento(UUID id, String nombre, int ventanaConfirmacionHoras) {
    }
}
