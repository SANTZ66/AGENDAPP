package com.fet.citas.simulador;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionSimulador {

    /** Compartido entre la semilla (lo llena) y el generador (lo lee). */
    @Bean
    public Escenario escenario() {
        return new Escenario();
    }
}
