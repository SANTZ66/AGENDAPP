package com.fet.citas.relacion.clientes;

import com.fet.citas.relacion.clientes.AgendaClient.CitaProxima;
import com.fet.citas.relacion.clientes.EstablecimientosClient.Establecimiento;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de los clientes REST contra un servidor HTTP falso (el del JDK).
 *
 * No hace falta Docker ni Kafka. Comprueban lo que importa de cada cliente:
 * que entiende la forma real de la respuesta del otro servicio (incluidos los
 * campos que no usa) y que, si el otro servicio no responde, devuelve una
 * lista vacia en vez de lanzar una excepcion.
 */
class ClientesRestTest {

    private HttpServer servidor;
    private String url;
    private final List<String> peticiones = new ArrayList<>();

    private static final String CITA1 = "11111111-1111-1111-1111-111111111111";
    private static final String CITA2 = "22222222-2222-2222-2222-222222222222";
    private static final String CITA3 = "33333333-3333-3333-3333-333333333333";
    private static final String EST = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private void responder(String ruta, int estado, java.util.function.Function<String, String> cuerpo) {
        servidor.createContext(ruta, ex -> {
            String consulta = ex.getRequestURI().getRawQuery();
            peticiones.add(ex.getRequestURI().getPath() + (consulta == null ? "" : "?" + consulta));
            byte[] bytes = cuerpo.apply(consulta == null ? "" : consulta).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(estado, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        servidor.start();
    }

    /** Una pagina con la misma forma que devuelve Spring Data en ms-agenda, con campos de sobra. */
    private static String pagina(boolean ultima, String... citas) {
        return "{\"content\":[" + String.join(",", citas) + "],"
                + "\"pageable\":{\"pageNumber\":0,\"pageSize\":100,\"sort\":{\"sorted\":true,\"empty\":false,\"unsorted\":false},\"offset\":0,\"paged\":true,\"unpaged\":false},"
                + "\"last\":" + ultima + ",\"totalElements\":3,\"totalPages\":2,\"first\":" + !ultima
                + ",\"size\":100,\"number\":0,\"numberOfElements\":" + citas.length + ",\"empty\":false}";
    }

    private static String cita(String id, String inicio, String confirmadaEn) {
        return "{\"id\":\"" + id + "\",\"establecimientoId\":\"" + EST + "\","
                + "\"profesionalId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\","
                + "\"servicioId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\","
                + "\"clienteId\":\"dddddddd-dddd-dddd-dddd-dddddddddddd\","
                + "\"inicio\":\"" + inicio + "\",\"fin\":\"2026-11-18T03:00:00Z\","
                + "\"estado\":\"agendada\",\"valorCobrado\":null,"
                + "\"confirmadaEn\":" + (confirmadaEn == null ? "null" : "\"" + confirmadaEn + "\"") + "}";
    }

    // ---------- AgendaClient ----------

    @Test
    void agenda_recorreTodasLasPaginas_yEntiendeLaRespuestaReal() {
        responder("/citas/proximas", 200, consulta -> consulta.contains("page=0")
                ? pagina(false, cita(CITA1, "2026-11-18T01:00:00Z", null),
                                cita(CITA2, "2026-11-18T02:00:00Z", "2026-11-17T20:01:17.585712Z"))
                : pagina(true, cita(CITA3, "2026-11-18T03:00:00Z", null)));

        List<CitaProxima> citas = new AgendaClient(url)
                .proximas(Instant.parse("2026-11-17T20:00:00Z"), Instant.parse("2026-11-18T20:00:00Z"));

        assertEquals(3, citas.size(), "las dos paginas");
        assertEquals(UUID.fromString(CITA1), citas.get(0).id());
        assertEquals(UUID.fromString(EST), citas.get(0).establecimientoId());
        assertEquals(OffsetDateTime.parse("2026-11-18T01:00:00Z"), citas.get(0).inicio());
        assertNull(citas.get(0).confirmadaEn(), "sin confirmar");
        assertEquals(OffsetDateTime.parse("2026-11-17T20:01:17.585712Z"), citas.get(1).confirmadaEn());
        assertEquals(UUID.fromString(CITA3), citas.get(2).id(), "la de la segunda pagina");
    }

    @Test
    void agenda_pideFechasEnUtc_conPaginacion() {
        responder("/citas/proximas", 200, consulta -> pagina(true));

        new AgendaClient(url).proximas(Instant.parse("2026-11-17T20:00:00.123456789Z"),
                Instant.parse("2026-11-18T20:00:00Z"));

        assertEquals(1, peticiones.size());
        assertEquals("/citas/proximas?desde=2026-11-17T20:00:00Z&hasta=2026-11-18T20:00:00Z&page=0&size=100",
                peticiones.get(0), "UTC (Z), segundos enteros, pagina 0 y 100 por pagina");
    }

    @Test
    void agenda_conLaPrimeraPaginaVacia_devuelveVacio() {
        responder("/citas/proximas", 200, consulta -> pagina(true));
        assertTrue(new AgendaClient(url).proximas(Instant.now(), Instant.now().plusSeconds(3600)).isEmpty());
    }

    @Test
    void agenda_siElServidorFalla_devuelveVacio_sinLanzar() {
        responder("/citas/proximas", 500, consulta -> "{\"error\":\"boom\"}");
        assertTrue(new AgendaClient(url).proximas(Instant.now(), Instant.now().plusSeconds(3600)).isEmpty());
    }

    @Test
    void agenda_siNoHayServidor_devuelveVacio_sinLanzar() throws IOException {
        assertTrue(new AgendaClient(urlSinServidor()).proximas(Instant.now(), Instant.now().plusSeconds(3600)).isEmpty());
    }

    // ---------- EstablecimientosClient ----------

    @Test
    void establecimientos_leeLaVentana_yIgnoraLosCamposQueNoUsa() {
        responder("/establecimientos", 200, consulta -> "["
                + "{\"id\":\"" + EST + "\",\"nombre\":\"Salon A\",\"telefono\":\"3000000000\",\"ventanaConfirmacionHoras\":24},"
                + "{\"id\":\"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee\",\"nombre\":\"Salon B\",\"telefono\":null,\"ventanaConfirmacionHoras\":72}]");

        List<Establecimiento> lista = new EstablecimientosClient(url).listar();

        assertEquals(2, lista.size());
        assertEquals(UUID.fromString(EST), lista.get(0).id());
        assertEquals("Salon A", lista.get(0).nombre());
        assertEquals(24, lista.get(0).ventanaConfirmacionHoras());
        assertEquals(72, lista.get(1).ventanaConfirmacionHoras());
    }

    @Test
    void establecimientos_conListaVacia_devuelveVacio() {
        responder("/establecimientos", 200, consulta -> "[]");
        assertTrue(new EstablecimientosClient(url).listar().isEmpty());
    }

    @Test
    void establecimientos_siElServidorFalla_devuelveVacio_sinLanzar() {
        responder("/establecimientos", 503, consulta -> "{}");
        assertTrue(new EstablecimientosClient(url).listar().isEmpty());
    }

    @Test
    void establecimientos_siNoHayServidor_devuelveVacio_sinLanzar() throws IOException {
        assertTrue(new EstablecimientosClient(urlSinServidor()).listar().isEmpty());
    }

    /** Un puerto en el que seguro no escucha nadie. */
    private static String urlSinServidor() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            return "http://127.0.0.1:" + s.getLocalPort();
        }
    }
}
