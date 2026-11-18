package com.fet.citas.agenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque de ms-agenda.
 *
 * Al ejecutar `mvn spring-boot:run` (o el jar), Spring Boot levanta un
 * servidor web embebido (Tomcat) en el puerto 8080 y conecta con la base
 * de datos configurada en application.properties.
 */
@SpringBootApplication
public class MsAgendaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsAgendaApplication.class, args);
    }
}
