package com.fet.citas.relacion.dominio;

import com.fet.citas.relacion.clientes.AgendaClient;
import com.fet.citas.relacion.clientes.AgendaClient.CitaProxima;
import com.fet.citas.relacion.clientes.EstablecimientosClient;
import com.fet.citas.relacion.clientes.EstablecimientosClient.Establecimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registra un recordatorio para las citas que estan por llegar.
 *
 * Cada cierto tiempo pregunta a ms-agenda que citas agendadas empiezan pronto
 * y a ms-establecimientos cuanta ventana de confirmacion tiene cada negocio.
 * Una cita se recuerda cuando entra en esa ventana, o sea cuando faltan
 * ventana horas o menos para que empiece:
 *
 *     ahora < inicio <= ahora + ventana del establecimiento
 *
 * y solo si la clienta aun no la ha confirmado. La ventana aqui solo decide
 * CUANDO recordar: no libera cupos ni anula nada.
 *
 * No guarda estado en memoria. La idempotencia la da la base de datos
 * (UNIQUE cita_id, tipo), asi que repetir la vuelta, reiniciar el servicio o
 * correr dos copias a la vez no duplica nada.
 *
 * Solo existe si app.recordatorios.activo=true. Por defecto esta apagado.
 */
@Component
@ConditionalOnProperty(name = "app.recordatorios.activo", havingValue = "true")
public class GeneradorRecordatorios {

    private static final Logger log = LoggerFactory.getLogger(GeneradorRecordatorios.class);

    private final AgendaClient agenda;
    private final EstablecimientosClient establecimientos;
    private final RelacionService service;

    public GeneradorRecordatorios(AgendaClient agenda,
                                  EstablecimientosClient establecimientos,
                                  RelacionService service) {
        this.agenda = agenda;
        this.establecimientos = establecimientos;
        this.service = service;
    }

    /** La vuelta programada. fixedDelay: la siguiente empieza cuando termina esta. */
    @Scheduled(fixedDelayString = "${app.recordatorios.cada-ms:60000}",
               initialDelayString = "${app.recordatorios.espera-inicial-ms:30000}")
    public void programado() {
        revisar(Instant.now());
    }

    /**
     * Una vuelta de revision.
     *
     * @param ahora el instante de referencia (se recibe para poder probar con fechas fijas)
     * @return cuantos recordatorios NUEVOS se registraron
     */
    public int revisar(Instant ahora) {
        // 1. La ventana de cada establecimiento. Sin ella no se puede decidir nada.
        List<Establecimiento> lista = establecimientos.listar();
        if (lista.isEmpty()) {
            log.warn("Recordatorios: no hay establecimientos (o ms-establecimientos no responde); "
                    + "se reintenta en la siguiente vuelta");
            return 0;
        }
        Map<UUID, Integer> ventanas = new HashMap<>();
        lista.forEach(e -> ventanas.put(e.id(), e.ventanaConfirmacionHoras()));
        int maxima = ventanas.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        // 2. Una sola consulta a ms-agenda con la ventana mas ancha; despues se
        //    filtra por la de cada establecimiento.
        List<CitaProxima> citas = agenda.proximas(ahora, ahora.plus(maxima, ChronoUnit.HOURS));

        int nuevos = 0;
        int yaConfirmadas = 0;
        int fueraDeVentana = 0;
        int sinVentana = 0;
        int yaRecordadas = 0;

        for (CitaProxima cita : citas) {
            if (cita.confirmadaEn() != null) {
                yaConfirmadas++;
                continue;
            }
            Integer ventana = ventanas.get(cita.establecimientoId());
            if (ventana == null) {
                sinVentana++;
                continue;
            }
            Instant inicio = cita.inicio().toInstant();
            if (!inicio.isAfter(ahora) || inicio.isAfter(ahora.plus(ventana, ChronoUnit.HOURS))) {
                fueraDeVentana++;
                continue;
            }
            // 3. Una transaccion por cita: si una falla, las demas siguen.
            try {
                if (service.recordatorio(cita.establecimientoId(), cita.id(), cita.inicio())) {
                    nuevos++;
                } else {
                    yaRecordadas++;
                }
            } catch (DataIntegrityViolationException e) {
                // Otra copia del servicio se nos adelanto: el recordatorio ya existe.
                yaRecordadas++;
                log.info("Recordatorio de la cita {} ya registrado por otra via, se ignora", cita.id());
            }
        }

        log.info("Recordatorios: {} nuevos | {} ya existian | {} ya confirmadas | {} fuera de ventana "
                        + "| {} de establecimientos desconocidos | {} citas revisadas",
                nuevos, yaRecordadas, yaConfirmadas, fueraDeVentana, sinVentana, citas.size());
        return nuevos;
    }
}
