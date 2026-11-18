package com.fet.citas.operaciones.dominio;

import com.fet.citas.operaciones.clientes.CatalogoClient;
import com.fet.citas.operaciones.eventos.EventoCita;
import com.fet.citas.operaciones.eventos.EventoInsumoBajo;
import com.fet.citas.operaciones.persistencia.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Que hacer cuando se presta un servicio: registrar el ingreso y descontar
 * lo que se consumio.
 *
 * Los dos procesos vienen del MISMO evento pero son independientes entre
 * si: si falla el descuento de insumos, el ingreso igual queda registrado.
 */
@Service
public class OperacionesService {

    private static final Logger log = LoggerFactory.getLogger(OperacionesService.class);
    public static final String TOPIC_INSUMO_BAJO = "inventario.insumo-bajo";

    private final LiquidacionRepository liquidaciones;
    private final InsumoRepository insumos;
    private final MovimientoInsumoRepository movimientos;
    private final CatalogoClient catalogo;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public OperacionesService(LiquidacionRepository liquidaciones,
                              InsumoRepository insumos,
                              MovimientoInsumoRepository movimientos,
                              CatalogoClient catalogo,
                              KafkaTemplate<String, String> kafka,
                              ObjectMapper json) {
        this.liquidaciones = liquidaciones;
        this.insumos = insumos;
        this.movimientos = movimientos;
        this.catalogo = catalogo;
        this.kafka = kafka;
        this.json = json;
    }

    /**
     * Procesa una cita completada.
     *
     * Es idempotente (RNF-04): si el mismo evento llega dos veces, la
     * segunda no hace nada. Kafka garantiza entrega "al menos una vez",
     * asi que esto no es opcional.
     */
    @Transactional
    public void procesarCitaCompletada(EventoCita evento) {
        if (liquidaciones.existsByCitaId(evento.citaId())) {
            log.info("Cita {} ya procesada, se ignora la reentrega", evento.citaId());
            return;
        }

        liquidaciones.save(new Liquidacion(
                evento.citaId(),
                evento.establecimientoId(),
                evento.profesionalId(),
                evento.servicioId(),
                evento.valorCobrado(),
                evento.inicio()));
        log.info("Ingreso liquidado: cita {} por {}", evento.citaId(), evento.valorCobrado());

        descontarInsumos(evento);
    }

    /** Descuenta del inventario lo que consume el servicio prestado. */
    private void descontarInsumos(EventoCita evento) {
        List<CatalogoClient.InsumoDeServicio> receta = catalogo.receta(evento.servicioId());
        if (receta.isEmpty()) {
            log.info("El servicio {} no tiene insumos asociados", evento.servicioId());
            return;
        }
        for (CatalogoClient.InsumoDeServicio linea : receta) {
            Optional<Insumo> encontrado = insumos.findById(linea.insumoId());
            if (encontrado.isEmpty()) {
                log.warn("El insumo {} no existe en el inventario", linea.insumoId());
                continue;
            }
            Insumo insumo = encontrado.get();
            insumo.descontar(linea.cantidadEstimada());
            insumos.save(insumo);
            movimientos.save(new MovimientoInsumo(
                    insumo.getId(), evento.citaId(), linea.cantidadEstimada()));
            log.info("Descontado {} de {}. Quedan {}",
                    linea.cantidadEstimada(), insumo.getNombre(), insumo.getExistencia());

            if (insumo.enUmbral()) {
                publicarInsumoBajo(insumo);
            }
        }
    }

    /** Avisa que hay que reponer. Aqui ms-operaciones actua como productor. */
    private void publicarInsumoBajo(Insumo insumo) {
        try {
            EventoInsumoBajo evento = new EventoInsumoBajo(
                    insumo.getId(), insumo.getEstablecimientoId(), insumo.getNombre(),
                    insumo.getExistencia(), insumo.getUmbralMinimo(), OffsetDateTime.now());
            kafka.send(TOPIC_INSUMO_BAJO,
                    insumo.getEstablecimientoId().toString(),
                    json.writeValueAsString(evento));
            log.info("Alerta publicada: {} esta en umbral", insumo.getNombre());
        } catch (Exception e) {
            log.error("No se pudo publicar la alerta de {}: {}",
                    insumo.getNombre(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Liquidacion> liquidaciones(UUID establecimientoId,
                                           OffsetDateTime desde, OffsetDateTime hasta) {
        return liquidaciones.findByEstablecimientoIdAndFechaServicioBetween(
                establecimientoId, desde, hasta);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalIngresos(UUID establecimientoId,
                                    OffsetDateTime desde, OffsetDateTime hasta) {
        return liquidaciones(establecimientoId, desde, hasta).stream()
                .map(Liquidacion::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
