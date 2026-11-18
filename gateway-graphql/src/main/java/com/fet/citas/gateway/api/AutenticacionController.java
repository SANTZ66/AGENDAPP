package com.fet.citas.gateway.api;

import com.fet.citas.gateway.clientes.ServiciosInternos;
import com.fet.citas.gateway.seguridad.EmisorJwt;
import com.fet.citas.gateway.seguridad.NoAutenticadoException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;
import java.util.UUID;

/**
 * Inicio de sesion del panel del establecimiento.
 *
 * ms-establecimientos confirma quien es la persona (es dueno de los usuarios);
 * el gateway fabrica el token. Esta mutacion es publica: es la que lo emite.
 */
@Controller
public class AutenticacionController {

    private static final String MENSAJE = "Correo o clave incorrectos";

    private final ServiciosInternos servicios;
    private final EmisorJwt emisor;

    public AutenticacionController(ServiciosInternos servicios, EmisorJwt emisor) {
        this.servicios = servicios;
        this.emisor = emisor;
    }

    @MutationMapping
    public SesionNegocio login(@Argument String correo, @Argument String clave) {
        UsuarioVerificado usuario;
        try {
            usuario = servicios.establecimientos.post()
                    .uri("/auth/verificar")
                    .body(Map.of("correo", correo, "clave", clave))
                    .retrieve().body(UsuarioVerificado.class);
        } catch (HttpClientErrorException e) {
            // 401 por credenciales; 400 si llegan vacias. Para quien intenta
            // entrar es lo mismo y no se le explica la diferencia.
            throw new NoAutenticadoException(MENSAJE);
        }
        if (usuario == null) {
            throw new NoAutenticadoException(MENSAJE);
        }

        String token = emisor.emitir(usuario.usuarioId(), usuario.establecimientoId(),
                usuario.nombre(), usuario.rol());
        return new SesionNegocio(token, emisor.validar(token).expiraEn().toString(),
                usuario.nombre(), usuario.establecimientoId(), usuario.rol());
    }

    /** Lo que responde ms-establecimientos en POST /auth/verificar. */
    record UsuarioVerificado(UUID usuarioId, String nombre, UUID establecimientoId, String rol) {
    }

    /** El tipo SesionNegocio del esquema. */
    record SesionNegocio(String token, String expiraEn, String nombre,
                         UUID establecimientoId, String rol) {
    }
}
