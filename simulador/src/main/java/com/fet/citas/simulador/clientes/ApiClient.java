package com.fet.citas.simulador.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Las APIs que el simulador necesita para generar actividad. */
@Component
public class ApiClient {

    public final RestClient establecimientos;
    public final RestClient agenda;
    public final RestClient operaciones;

    public ApiClient(@Value("${app.establecimientos.url}") String urlEstablecimientos,
                     @Value("${app.agenda.url}") String urlAgenda,
                     @Value("${app.operaciones.url}") String urlOperaciones) {
        this.establecimientos = RestClient.builder().baseUrl(urlEstablecimientos).build();
        this.agenda = RestClient.builder().baseUrl(urlAgenda).build();
        this.operaciones = RestClient.builder().baseUrl(urlOperaciones).build();
    }
}
