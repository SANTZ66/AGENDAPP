package com.fet.citas.establecimientos;

import com.fet.citas.establecimientos.dominio.Establecimiento;
import com.fet.citas.establecimientos.dominio.Usuario;
import com.fet.citas.establecimientos.persistencia.EstablecimientoRepository;
import com.fet.citas.establecimientos.persistencia.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Crea un usuario de prueba para poder entrar al panel.
 *
 * Los establecimientos no existen al arrancar: los crea el simulador unos
 * segundos despues, con un identificador que nadie conoce de antemano. Por eso
 * esta tarea reintenta cada pocos segundos y deja de hacer algo en cuanto ya
 * hay un usuario. Queda ligado al establecimiento mas antiguo.
 *
 * Se desactiva con app.auth.semilla.activa=false. Las credenciales de prueba
 * son solo para desarrollo: en un entorno real hay que desactivarla.
 */
@Component
public class SembradoUsuarios {

    private static final Logger log = LoggerFactory.getLogger(SembradoUsuarios.class);

    private final UsuarioRepository usuarios;
    private final EstablecimientoRepository establecimientos;
    private final PasswordEncoder codificador;
    private final boolean activa;
    private final String correo;
    private final String clave;

    public SembradoUsuarios(UsuarioRepository usuarios,
                            EstablecimientoRepository establecimientos,
                            PasswordEncoder codificador,
                            @Value("${app.auth.semilla.activa:true}") boolean activa,
                            @Value("${app.auth.semilla.correo:admin@demo.com}") String correo,
                            @Value("${app.auth.semilla.clave:demo1234}") String clave) {
        this.usuarios = usuarios;
        this.establecimientos = establecimientos;
        this.codificador = codificador;
        this.activa = activa;
        this.correo = correo;
        this.clave = clave;
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    public void sembrar() {
        if (!activa || usuarios.count() > 0) {
            return;
        }
        establecimientos.findAll().stream()
                .min(Comparator.comparing(Establecimiento::getCreadoEn))
                .ifPresent(e -> {
                    usuarios.save(new Usuario(e.getId(), correo,
                            codificador.encode(clave), "Administrador", "ADMIN"));
                    log.info("Usuario de prueba {} creado para el establecimiento {}",
                            correo, e.getId());
                });
    }
}
