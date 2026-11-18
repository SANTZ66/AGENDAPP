package com.fet.citas.relacion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enciende las tareas programadas (@Scheduled) de este servicio.
 *
 * Hasta ahora ms-relacion-cliente solo reaccionaba a eventos de Kafka. Con
 * esto puede ademas hacer cosas por su cuenta cuando llega la hora, como
 * recordar una cita proxima. Por si solo no ejecuta nada: cada tarea se
 * declara aparte.
 */
@Configuration
@EnableScheduling
public class Planificador {
}
