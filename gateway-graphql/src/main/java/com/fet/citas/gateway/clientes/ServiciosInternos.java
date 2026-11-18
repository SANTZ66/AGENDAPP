package com.fet.citas.gateway.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Las conexiones del gateway con los servicios internos.
 *
 * El gateway no tiene base de datos ni logica de negocio: solo sabe a quien
 * preguntarle cada cosa y como juntar las respuestas.
 */
@Component
public class ServiciosInternos {

    public final RestClient establecimientos;
    public final RestClient agenda;
    public final RestClient operaciones;
    public final RestClient relacion;

    public ServiciosInternos(
            @Value("${app.servicios.establecimientos}") String urlEstablecimientos,
            @Value("${app.servicios.agenda}") String urlAgenda,
            @Value("${app.servicios.operaciones}") String urlOperaciones,
            @Value("${app.servicios.relacion}") String urlRelacion) {
        this.establecimientos = RestClient.builder().baseUrl(urlEstablecimientos).build();
        this.agenda = RestClient.builder().baseUrl(urlAgenda).build();
        this.operaciones = RestClient.builder().baseUrl(urlOperaciones).build();
        this.relacion = RestClient.builder().baseUrl(urlRelacion).build();
    }
}
