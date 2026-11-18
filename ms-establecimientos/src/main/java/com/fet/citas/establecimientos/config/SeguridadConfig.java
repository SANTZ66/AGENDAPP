package com.fet.citas.establecimientos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pieza de apoyo del inicio de sesion: el cifrado de claves (BCrypt) y el
 * planificador que usa el sembrado del usuario de prueba.
 */
@Configuration
@EnableScheduling
public class SeguridadConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
