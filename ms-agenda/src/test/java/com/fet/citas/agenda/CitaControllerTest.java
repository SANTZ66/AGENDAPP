package com.fet.citas.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fet.citas.agenda.persistencia.CitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integracion de la API de citas.
 *
 * Levantan la aplicacion completa contra una base H2 en memoria y hacen
 * peticiones HTTP reales con MockMvc. Cubren los caminos que la rubrica
 * pide poder demostrar: creacion exitosa, validacion (400), conflicto de
 * cupo (409) y cita inexistente (404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CitaControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CitaRepository repo;

    @BeforeEach
    void limpiar() {
        repo.deleteAll();
    }

    private Map<String, Object> reservaValida(String inicio, String fin) {
        return Map.of(
                "establecimientoId", UUID.randomUUID().toString(),
                "profesionalId", "22222222-2222-2222-2222-222222222222",
                "servicioId", UUID.randomUUID().toString(),
                "clienteId", UUID.randomUUID().toString(),
                "inicio", inicio,
                "fin", fin
        );
    }

    @Test
    void reservar_devuelve201_yPersiste() throws Exception {
        var body = reservaValida("2026-10-01T09:00:00-05:00",
                                 "2026-10-01T10:00:00-05:00");
        mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("agendada"));
    }

    @Test
    void reservar_sinCampos_devuelve400() throws Exception {
        // Falta todo menos profesionalId -> validacion debe rechazar
        mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profesionalId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void reservar_mismoCupoDosVeces_devuelve409() throws Exception {
        var body = reservaValida("2026-10-01T11:00:00-05:00",
                                 "2026-10-01T12:00:00-05:00");
        String payload = json.writeValueAsString(body);

        // Primera reserva: OK
        mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // Segunda reserva al mismo profesional y hora: conflicto
        mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cerrar_citaInexistente_devuelve404() throws Exception {
        mvc.perform(post("/citas/" + UUID.randomUUID() + "/cierre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorCobrado\": 45000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- confirmacion de la clienta ----------

    /** Reserva una cita y devuelve su id. */
    private String reservar(String inicio, String fin) throws Exception {
        String resp = mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reservaValida(inicio, fin))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("id").asText();
    }

    @Test
    void reservar_laCitaNaceSinConfirmar() throws Exception {
        mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reservaValida(
                                "2026-10-03T09:00:00-05:00", "2026-10-03T10:00:00-05:00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmadaEn").isEmpty());
    }

    @Test
    void confirmar_citaAgendada_devuelve200ConFecha_ySigueAgendada() throws Exception {
        String id = reservar("2026-10-03T10:00:00-05:00", "2026-10-03T11:00:00-05:00");

        mvc.perform(post("/citas/" + id + "/confirmacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("agendada"))
                .andExpect(jsonPath("$.confirmadaEn").isNotEmpty());
    }

    @Test
    void confirmar_esIdempotente_yConservaLaFechaOriginal() throws Exception {
        String id = reservar("2026-10-03T11:00:00-05:00", "2026-10-03T12:00:00-05:00");

        String primera = json.readTree(mvc.perform(post("/citas/" + id + "/confirmacion"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("confirmadaEn").asText();
        String segunda = json.readTree(mvc.perform(post("/citas/" + id + "/confirmacion"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("confirmadaEn").asText();

        org.junit.jupiter.api.Assertions.assertEquals(primera, segunda,
                "confirmar otra vez no debe cambiar la fecha de la primera confirmacion");
    }

    @Test
    void confirmar_citaInexistente_devuelve404() throws Exception {
        mvc.perform(post("/citas/" + UUID.randomUUID() + "/confirmacion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void confirmar_citaCancelada_devuelve409() throws Exception {
        String id = reservar("2026-10-03T12:00:00-05:00", "2026-10-03T13:00:00-05:00");
        mvc.perform(post("/citas/" + id + "/cancelacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canceladaPor\":\"cliente\",\"motivo\":\"prueba\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/citas/" + id + "/confirmacion"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void confirmar_citaCompletada_devuelve409() throws Exception {
        String id = reservar("2026-10-03T13:00:00-05:00", "2026-10-03T14:00:00-05:00");
        mvc.perform(post("/citas/" + id + "/cierre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorCobrado\": 45000}"))
                .andExpect(status().isOk());

        mvc.perform(post("/citas/" + id + "/confirmacion"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmar_noCambiaCerrarNiCancelar() throws Exception {
        // Una cita confirmada sigue siendo agendada, asi que se puede cerrar como siempre.
        String id = reservar("2026-10-03T14:00:00-05:00", "2026-10-03T15:00:00-05:00");
        mvc.perform(post("/citas/" + id + "/confirmacion")).andExpect(status().isOk());

        mvc.perform(post("/citas/" + id + "/cierre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorCobrado\": 45000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("completada"))
                // la fecha de confirmacion se conserva en la cita ya atendida
                .andExpect(jsonPath("$.confirmadaEn").isNotEmpty());
    }

    // ---------- GET /citas/proximas ----------

    private static String ahoraMas(long horas) {
        return java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .plusHours(horas).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString();
    }

    private static String ahoraMas(long horas, long extraHoras) {
        return java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .plusHours(horas + extraHoras).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString();
    }

    @Test
    void proximas_devuelveSoloAgendadasDentroDelRango_enOrden() throws Exception {
        // A (+5 h) y B (+10 h) deben aparecer; C (+50 h) cae fuera del rango;
        // D (+7 h) esta cancelada; E (+8 h) esta completada.
        String a = reservar(ahoraMas(5), ahoraMas(5, 1));
        String b = reservar(ahoraMas(10), ahoraMas(10, 1));
        reservar(ahoraMas(50), ahoraMas(50, 1));
        String d = reservar(ahoraMas(7), ahoraMas(7, 1));
        mvc.perform(post("/citas/" + d + "/cancelacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canceladaPor\":\"cliente\",\"motivo\":\"x\"}"))
                .andExpect(status().isOk());
        String e = reservar(ahoraMas(8), ahoraMas(8, 1));
        mvc.perform(post("/citas/" + e + "/cierre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorCobrado\": 1}"))
                .andExpect(status().isOk());

        mvc.perform(get("/citas/proximas")
                        .param("desde", ahoraMas(0))
                        .param("hasta", ahoraMas(24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(a))
                .andExpect(jsonPath("$.content[1].id").value(b))
                .andExpect(jsonPath("$.content[0].estado").value("agendada"))
                .andExpect(jsonPath("$.content[0].confirmadaEn").isEmpty());
    }

    @Test
    void proximas_incluyeCitasDeCualquierProfesional() throws Exception {
        // reservaValida() usa siempre el mismo profesional; aqui se usa otro distinto.
        var otro = new java.util.HashMap<>(reservaValida(ahoraMas(6), ahoraMas(6, 1)));
        otro.put("profesionalId", "33333333-3333-3333-3333-333333333333");
        mvc.perform(post("/citas").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(otro)))
                .andExpect(status().isCreated());
        reservar(ahoraMas(5), ahoraMas(5, 1));

        mvc.perform(get("/citas/proximas")
                        .param("desde", ahoraMas(0)).param("hasta", ahoraMas(24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void proximas_reflejaLaConfirmacion() throws Exception {
        String id = reservar(ahoraMas(5), ahoraMas(5, 1));
        mvc.perform(post("/citas/" + id + "/confirmacion")).andExpect(status().isOk());

        mvc.perform(get("/citas/proximas")
                        .param("desde", ahoraMas(0)).param("hasta", ahoraMas(24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].confirmadaEn").isNotEmpty());
    }

    @Test
    void proximas_paginaLosResultados() throws Exception {
        reservar(ahoraMas(5), ahoraMas(5, 1));
        reservar(ahoraMas(6), ahoraMas(6, 1));
        reservar(ahoraMas(7), ahoraMas(7, 1));

        mvc.perform(get("/citas/proximas")
                        .param("desde", ahoraMas(0)).param("hasta", ahoraMas(24))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mvc.perform(get("/citas/proximas")
                        .param("desde", ahoraMas(0)).param("hasta", ahoraMas(24))
                        .param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void proximas_sinFechas_devuelve400() throws Exception {
        mvc.perform(get("/citas/proximas"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cerrar_citaAgendada_devuelve200yCompletada() throws Exception {
        // Reservar primero
        var body = reservaValida("2026-10-02T09:00:00-05:00",
                                 "2026-10-02T10:00:00-05:00");
        String resp = mvc.perform(post("/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(resp).get("id").asText();

        // Cerrar
        mvc.perform(post("/citas/" + id + "/cierre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorCobrado\": 45000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("completada"))
                .andExpect(jsonPath("$.valorCobrado").value(45000));
    }
}
