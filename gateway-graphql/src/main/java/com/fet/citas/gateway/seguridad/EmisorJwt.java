package com.fet.citas.gateway.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emite y valida los tokens de sesion del panel del establecimiento.
 *
 * Firma HS256 con un secreto que vive solo en este servicio. ms-establecimientos
 * confirma quien es la persona; el token lo fabrica y lo revisa unicamente el
 * gateway, que es el unico punto de entrada.
 *
 * Contenido del token: sub = usuario, est = establecimiento, nom, rol y exp.
 */
@Component
public class EmisorJwt {

    private final SecretKey llave;
    private final Duration duracion;
    private final Clock reloj;

    @Autowired
    public EmisorJwt(@Value("${app.jwt.secreto}") String secreto,
                     @Value("${app.jwt.minutos}") long minutos) {
        this(secreto, minutos, Clock.systemUTC());
    }

    /** El reloj se puede cambiar para probar tokens vencidos sin esperar. */
    EmisorJwt(String secreto, long minutos, Clock reloj) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException(
                    "app.jwt.secreto debe tener al menos 32 caracteres (HS256)");
        }
        if (minutos <= 0) {
            throw new IllegalArgumentException("app.jwt.minutos debe ser mayor que cero");
        }
        this.llave = Keys.hmacShaKeyFor(bytes);
        this.duracion = Duration.ofMinutes(minutos);
        this.reloj = reloj;
    }

    public String emitir(UUID usuarioId, UUID establecimientoId, String nombre, String rol) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("est", establecimientoId.toString())
                .claim("nom", nombre)
                .claim("rol", rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(llave)
                .compact();
    }

    /**
     * @throws TokenInvalidoException si esta vencido, alterado o mal formado
     */
    public Sesion validar(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(llave)
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new Sesion(
                    UUID.fromString(c.getSubject()),
                    UUID.fromString(c.get("est", String.class)),
                    c.get("nom", String.class),
                    c.get("rol", String.class),
                    c.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new TokenInvalidoException("La sesion vencio", true, e);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new TokenInvalidoException("Token no valido", false, e);
        }
    }
}
