package com.fet.citas.operaciones.clientes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Llamada REST a ms-establecimientos para conocer la receta de un servicio.
 *
 * Es comunicacion SINCRONA entre servicios: ms-operaciones necesita el dato
 * en ese momento para poder descontar. El evento de Kafka trae el hecho
 * ("se completo esta cita"), no el catalogo.
 */
@Component
public class CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);

    private final RestClient rest;

    public CatalogoClient(@Value("${app.establecimientos.url}") String baseUrl) {
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Que insumos consume el servicio y en que cantidad. */
    public List<InsumoDeServicio> receta(UUID servicioId) {
        try {
            InsumoDeServicio[] receta = rest.get()
                    .uri("/servicios/{id}/insumos", servicioId)
                    .retrieve()
                    .body(InsumoDeServicio[].class);
            return receta == null ? List.of() : List.of(receta);
        } catch (Exception e) {
            log.warn("No se pudo consultar la receta del servicio {}: {}",
                    servicioId, e.getMessage());
            return List.of();
        }
    }

    public record InsumoDeServicio(UUID insumoId, BigDecimal cantidadEstimada) {
    }
}
