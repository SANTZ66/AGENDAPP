package com.fet.citas.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Permisos de origen cruzado (CORS).
 *
 * Un navegador considera que http://localhost:3000 y http://localhost:8000 son
 * origenes distintos, y por seguridad bloquea las peticiones entre ellos salvo
 * que el servidor las autorice. Sin esto, cualquier frontend servido aparte
 * fallaria en todas sus llamadas sin dar un error claro.
 *
 * El panel incluido en este servicio NO necesita esta configuracion, porque se
 * sirve desde el mismo origen que la API. Esta aqui para el dia en que el
 * frontend se desarrolle por separado, que es lo habitual con React o Angular.
 *
 * Los origenes permitidos son los puertos de desarrollo mas comunes. En un
 * despliegue real se limitarian al dominio del frontend.
 */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registro) {
        registro.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:3000",   // React, Next.js
                        "http://localhost:5173",   // Vite
                        "http://localhost:4200",   // Angular
                        "http://localhost:8000")   // el panel incluido
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
