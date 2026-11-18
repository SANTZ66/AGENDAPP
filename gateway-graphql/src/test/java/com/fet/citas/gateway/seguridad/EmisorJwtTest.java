package com.fet.citas.gateway.seguridad;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmisorJwtTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres-ok";

    private final UUID usuario = UUID.randomUUID();
    private final UUID establecimiento = UUID.randomUUID();

    @Test
    void tokenValidDevuelveLaSesion() {
        EmisorJwt emisor = new EmisorJwt(SECRETO, 60);

        String token = emisor.emitir(usuario, establecimiento, "Administrador", "ADMIN");
        Sesion sesion = emisor.validar(token);

        assertEquals(usuario, sesion.usuarioId());
        assertEquals(establecimiento, sesion.establecimientoId());
        assertEquals("Administrador", sesion.nombre());
        assertEquals("ADMIN", sesion.rol());
        assertTrue(sesion.expiraEn().isAfter(Instant.now()));
    }

    @Test
    void tokenVencidoSeRechazaComoVencido() {
        // Emitido hace 10 horas con una duracion de 60 minutos.
        Clock hace10Horas = Clock.fixed(Instant.now().minus(Duration.ofHours(10)), ZoneOffset.UTC);
        String token = new EmisorJwt(SECRETO, 60, hace10Horas)
                .emitir(usuario, establecimiento, "Administrador", "ADMIN");

        TokenInvalidoException e = assertThrows(TokenInvalidoException.class,
                () -> new EmisorJwt(SECRETO, 60).validar(token));

        assertTrue(e.vencido(), "debe marcarse como vencido, no como alterado");
    }

    @Test
    void firmaAlteradaSeRechaza() {
        EmisorJwt emisor = new EmisorJwt(SECRETO, 60);
        String[] partes = emisor.emitir(usuario, establecimiento, "Administrador", "ADMIN").split("\\.");

        // Se cambia un caracter del MEDIO de la firma. Cambiar el ultimo podria
        // tocar solo bits de relleno de base64 y dejar la firma igual.
        char[] firma = partes[2].toCharArray();
        firma[10] = firma[10] == 'A' ? 'B' : 'A';
        String alterado = partes[0] + "." + partes[1] + "." + new String(firma);

        TokenInvalidoException e = assertThrows(TokenInvalidoException.class,
                () -> emisor.validar(alterado));
        assertFalse(e.vencido());
    }

    @Test
    void contenidoAlteradoSeRechaza() {
        EmisorJwt emisor = new EmisorJwt(SECRETO, 60);
        String[] partes = emisor.emitir(usuario, establecimiento, "Administrador", "ADMIN").split("\\.");

        // Se reemplaza el establecimiento del contenido conservando la firma original.
        String otro = UUID.randomUUID().toString();
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace(establecimiento.toString(), otro);
        String alterado = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(contenido.getBytes(StandardCharsets.UTF_8))
                + "." + partes[2];

        assertThrows(TokenInvalidoException.class, () -> emisor.validar(alterado));
    }

    @Test
    void tokenFirmadoConOtroSecretoSeRechaza() {
        String token = new EmisorJwt("otro-secreto-distinto-de-mas-de-32-caracteres", 60)
                .emitir(usuario, establecimiento, "Administrador", "ADMIN");

        assertThrows(TokenInvalidoException.class,
                () -> new EmisorJwt(SECRETO, 60).validar(token));
    }

    @Test
    void textoQueNoEsTokenSeRechaza() {
        EmisorJwt emisor = new EmisorJwt(SECRETO, 60);
        assertThrows(TokenInvalidoException.class, () -> emisor.validar("esto-no-es-un-jwt"));
        assertThrows(TokenInvalidoException.class, () -> emisor.validar(""));
    }

    @Test
    void secretoCortoNoSeAcepta() {
        assertThrows(IllegalArgumentException.class, () -> new EmisorJwt("corto", 60));
    }
}
