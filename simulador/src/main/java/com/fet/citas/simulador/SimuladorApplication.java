package com.fet.citas.simulador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Generador de actividad sintetica.
 *
 * NO forma parte de la arquitectura entregada: es una herramienta que usa
 * el sistema desde afuera, igual que lo haria una aplicacion cliente.
 *
 * Decision de diseno importante: el simulador invoca la API REST de
 * ms-agenda y NO publica directamente en Kafka. Inyectar eventos a mano
 * crearia hechos que nunca pasaron por las validaciones de negocio ni por
 * la transaccion, y el estado de la base de datos quedaria desincronizado
 * del bus. Llamando a la API, los eventos los emite quien debe emitirlos.
 */
@SpringBootApplication
@EnableScheduling
public class SimuladorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimuladorApplication.class, args);
    }
}
