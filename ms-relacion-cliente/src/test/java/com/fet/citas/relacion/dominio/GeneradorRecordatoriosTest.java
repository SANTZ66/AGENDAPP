package com.fet.citas.relacion.dominio;

import com.fet.citas.relacion.clientes.AgendaClient;
import com.fet.citas.relacion.clientes.AgendaClient.CitaProxima;
import com.fet.citas.relacion.clientes.EstablecimientosClient;
import com.fet.citas.relacion.clientes.EstablecimientosClient.Establecimiento;
import com.fet.citas.relacion.persistencia.ClienteRecurrenciaRepository;
import com.fet.citas.relacion.persistencia.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas del generador de recordatorios con fechas fijas y sin base de
 * datos ni Kafka: los clientes REST y los repositorios son dobles.
 */
class GeneradorRecordatoriosTest {

    /** "Ahora" de las pruebas: 2026-11-17 15:00 en Colombia. */
    private static final Instant AHORA = Instant.parse("2026-11-17T20:00:00Z");

    private static final UUID EST_24H = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID EST_72H = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CITA_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CITA_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AgendaClient agenda = mock(AgendaClient.class);
    private final EstablecimientosClient establecimientos = mock(EstablecimientosClient.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    // El servicio REAL, con repositorios dobles: asi se prueba tambien el texto y la idempotencia.
    private final RelacionService service =
            new RelacionService(notificaciones, mock(ClienteRecurrenciaRepository.class), 28);
    private final GeneradorRecordatorios generador =
            new GeneradorRecordatorios(agenda, establecimientos, service);

    // ---------- ayudas ----------

    private void conEstablecimientos(Establecimiento... lista) {
        when(establecimientos.listar()).thenReturn(List.of(lista));
    }

    private void conCitas(CitaProxima... lista) {
        when(agenda.proximas(any(), any())).thenReturn(List.of(lista));
    }

    private static Establecimiento est(UUID id, int ventanaHoras) {
        return new Establecimiento(id, "Salon", ventanaHoras);
    }

    private static CitaProxima cita(UUID id, UUID est, Instant inicio, Instant confirmadaEn) {
        return new CitaProxima(id, est, inicio.atOffset(ZoneOffset.UTC),
                confirmadaEn == null ? null : confirmadaEn.atOffset(ZoneOffset.UTC));
    }

    private static Instant enHoras(long horas) {
        return AHORA.plus(horas, ChronoUnit.HOURS);
    }

    // ---------- dentro y fuera de la ventana ----------

    @Test
    void dentroDeLaVentana_registraElRecordatorio() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, Instant.parse("2026-11-18T15:00:00Z"), null));   // 19 h despues

        int nuevos = generador.revisar(AHORA);

        assertEquals(1, nuevos);
        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(guardada.capture());
        Notificacion n = guardada.getValue();
        assertEquals("recordatorio-cita", n.getTipo());
        assertEquals("Recuerda tu cita", n.getAsunto());
        assertEquals(CITA_1, n.getCitaId());
        assertEquals(EST_24H, n.getEstablecimientoId());
        assertEquals("simulado", n.getCanal(), "solo se registra, no se envia");
        assertEquals("registrada", n.getEstado());
    }

    @Test
    void elTextoUsaLaHoraDeColombia_noLaDelServidor() {
        conEstablecimientos(est(EST_24H, 24));
        // 15:00 UTC del 18 de noviembre = 10:00 en Colombia
        conCitas(cita(CITA_1, EST_24H, Instant.parse("2026-11-18T15:00:00Z"), null));

        generador.revisar(AHORA);

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(guardada.capture());
        String cuerpo = guardada.getValue().getCuerpo();
        assertTrue(cuerpo.contains("18/11/2026 10:00"), cuerpo);
        assertFalse(cuerpo.contains("15:00"), "no debe salir la hora UTC: " + cuerpo);
        assertTrue(cuerpo.contains("(hora de Colombia)"), cuerpo);
        assertTrue(cuerpo.contains("Mis citas"), "invita a confirmar en Mis citas: " + cuerpo);
        assertTrue(cuerpo.length() <= 500, "cabe en la columna cuerpo");
    }

    @Test
    void fueraDeLaVentana_noRegistra() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, enHoras(30), null));

        assertEquals(0, generador.revisar(AHORA));
        verify(notificaciones, never()).save(any());
    }

    @Test
    void elBordeDeLaVentana_cuentaPorDentro_ySeIncluyeSoloHastaElSegundoExacto() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, enHoras(24), null),                       // justo en el borde: entra
                cita(CITA_2, EST_24H, enHoras(24).plusSeconds(1), null));        // un segundo despues: no

        assertEquals(1, generador.revisar(AHORA));
        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(guardada.capture());
        assertEquals(CITA_1, guardada.getValue().getCitaId());
    }

    @Test
    void unaCitaQueYaEmpezo_noSeRecuerda() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, AHORA, null),                              // empieza justo ahora
                cita(CITA_2, EST_24H, AHORA.minusSeconds(60), null));            // empezo hace un minuto

        assertEquals(0, generador.revisar(AHORA));
        verify(notificaciones, never()).save(any());
    }

    @Test
    void cadaEstablecimientoUsaSuPropiaVentana() {
        conEstablecimientos(est(EST_24H, 24), est(EST_72H, 72));
        // Las dos citas empiezan dentro de 48 h: solo la del negocio de 72 h esta en ventana.
        conCitas(cita(CITA_1, EST_24H, enHoras(48), null),
                cita(CITA_2, EST_72H, enHoras(48), null));

        assertEquals(1, generador.revisar(AHORA));
        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(guardada.capture());
        assertEquals(CITA_2, guardada.getValue().getCitaId());
        assertEquals(EST_72H, guardada.getValue().getEstablecimientoId());
    }

    @Test
    void consultaAAgendaConLaVentanaMasAncha() {
        conEstablecimientos(est(EST_24H, 24), est(EST_72H, 72));
        conCitas();

        generador.revisar(AHORA);

        verify(agenda).proximas(AHORA, AHORA.plus(72, ChronoUnit.HOURS));
    }

    // ---------- confirmada, duplicado ----------

    @Test
    void unaCitaConfirmada_noGeneraRecordatorio() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, enHoras(10), AHORA.minusSeconds(600)));

        assertEquals(0, generador.revisar(AHORA));
        verify(notificaciones, never()).save(any());
    }

    @Test
    void siYaHayRecordatorio_seIgnora_yNoSeCreaOtro() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, enHoras(10), null));
        when(notificaciones.existsByCitaIdAndTipo(CITA_1, "recordatorio-cita")).thenReturn(true);

        assertEquals(0, generador.revisar(AHORA), "no cuenta como nuevo");
        verify(notificaciones, never()).save(any());
    }

    @Test
    void repetirLaVuelta_conLaBaseRealDeIdempotencia_noDuplica() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, enHoras(10), null));
        // Primera vuelta: no existe. Segunda: ya existe (como lo vera la base despues de guardar).
        when(notificaciones.existsByCitaIdAndTipo(CITA_1, "recordatorio-cita")).thenReturn(false, true);

        assertEquals(1, generador.revisar(AHORA));
        assertEquals(0, generador.revisar(AHORA));
        verify(notificaciones, times(1)).save(any());
    }

    @Test
    void siLaBaseRechazaElDuplicado_seCapturaYSigueConLasDemas() {
        RelacionService falso = mock(RelacionService.class);
        GeneradorRecordatorios g = new GeneradorRecordatorios(agenda, establecimientos, falso);
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, EST_24H, enHoras(5), null), cita(CITA_2, EST_24H, enHoras(6), null));
        when(falso.recordatorio(eq(EST_24H), eq(CITA_1), any()))
                .thenThrow(new DataIntegrityViolationException("UNIQUE cita_id,tipo"));
        when(falso.recordatorio(eq(EST_24H), eq(CITA_2), any())).thenReturn(true);

        assertDoesNotThrow(() -> assertEquals(1, g.revisar(AHORA)));
        verify(falso).recordatorio(eq(EST_24H), eq(CITA_1), any());
        verify(falso).recordatorio(eq(EST_24H), eq(CITA_2), any());
    }

    // ---------- el otro servicio no responde / datos incompletos ----------

    @Test
    void sinEstablecimientos_noConsultaCitas_ni_Registra() {
        when(establecimientos.listar()).thenReturn(List.of());

        assertEquals(0, generador.revisar(AHORA));
        verify(agenda, never()).proximas(any(), any());
        verify(notificaciones, never()).save(any());
    }

    @Test
    void sinCitas_noRegistraNada() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas();

        assertEquals(0, generador.revisar(AHORA));
        verify(notificaciones, never()).save(any());
    }

    @Test
    void unaCitaDeUnEstablecimientoDesconocido_seIgnora() {
        conEstablecimientos(est(EST_24H, 24));
        conCitas(cita(CITA_1, UUID.randomUUID(), enHoras(5), null));

        assertEquals(0, generador.revisar(AHORA));
        verify(notificaciones, never()).save(any());
    }

    // ---------- solo existe si esta activado ----------

    @Test
    void elJobSoloExisteConActivoTrue() {
        ApplicationContextRunner base = new ApplicationContextRunner()
                .withBean(AgendaClient.class, () -> agenda)
                .withBean(EstablecimientosClient.class, () -> establecimientos)
                .withBean(RelacionService.class, () -> service)
                .withUserConfiguration(GeneradorRecordatorios.class);

        base.run(ctx -> assertFalse(ctx.containsBean("generadorRecordatorios"),
                "sin la propiedad no debe existir"));
        base.withPropertyValues("app.recordatorios.activo=false")
                .run(ctx -> assertFalse(ctx.containsBean("generadorRecordatorios"), "con false tampoco"));
        base.withPropertyValues("app.recordatorios.activo=true")
                .run(ctx -> assertTrue(ctx.containsBean("generadorRecordatorios"), "con true si"));
    }

    @Test
    void recordatorio_usaElOffsetDeColombia_aunqueLaFechaVengaEnOtraZona() {
        // Misma hora, escrita con offset +09:00: el texto debe decir igual 10:00 de Colombia.
        OffsetDateTime enTokio = OffsetDateTime.parse("2026-11-19T00:00:00+09:00");   // = 2026-11-18T15:00Z
        service.recordatorio(EST_24H, CITA_1, enTokio);

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(guardada.capture());
        assertTrue(guardada.getValue().getCuerpo().contains("18/11/2026 10:00"), guardada.getValue().getCuerpo());
    }
}
