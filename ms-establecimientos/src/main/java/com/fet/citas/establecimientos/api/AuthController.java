package com.fet.citas.establecimientos.api;

import com.fet.citas.establecimientos.dominio.Usuario;
import com.fet.citas.establecimientos.dto.Peticiones.Credenciales;
import com.fet.citas.establecimientos.dto.Respuestas.UsuarioVerificado;
import com.fet.citas.establecimientos.error.ErrorResponse;
import com.fet.citas.establecimientos.persistencia.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Verificacion de credenciales del panel del establecimiento.
 *
 * Este servicio solo confirma quien es la persona. El token lo emite y lo
 * valida el gateway, que es el unico punto de entrada.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticacion", description = "Verificacion de credenciales del panel")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.codificador = codificador;
    }

    @Operation(summary = "Verifica correo y clave. No emite token")
    @PostMapping("/verificar")
    public ResponseEntity<?> verificar(@Valid @RequestBody Credenciales req) {
        Optional<Usuario> usuario = usuarios.findByCorreo(req.correo().trim().toLowerCase());

        // Mismo mensaje para correo inexistente, usuario inactivo y clave
        // equivocada: no se revela cual de las tres cosas fue.
        if (usuario.isEmpty() || !usuario.get().isActivo()
                || !codificador.matches(req.clave(), usuario.get().getClaveHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.de(401, "Unauthorized", "Correo o clave incorrectos"));
        }
        return ResponseEntity.ok(UsuarioVerificado.desde(usuario.get()));
    }
}
