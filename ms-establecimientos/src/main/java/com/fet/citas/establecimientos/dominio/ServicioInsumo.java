package com.fet.citas.establecimientos.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Que insumos consume un servicio y en que cantidad estimada.
 *
 * Es la "receta". ms-operaciones la consulta por REST cuando llega un
 * evento citas.completada, para saber cuanto descontar del inventario.
 */
@Entity
@Table(name = "servicio_insumo", schema = "establecimientos")
public class ServicioInsumo {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "servicio_id", nullable = false)
    private UUID servicioId;

    @Column(name = "insumo_id", nullable = false)
    private UUID insumoId;

    @Column(name = "cantidad_estimada", nullable = false)
    private BigDecimal cantidadEstimada;

    protected ServicioInsumo() {
    }

    public ServicioInsumo(UUID servicioId, UUID insumoId, BigDecimal cantidadEstimada) {
        this.servicioId = servicioId;
        this.insumoId = insumoId;
        this.cantidadEstimada = cantidadEstimada;
    }

    public UUID getId() { return id; }
    public UUID getServicioId() { return servicioId; }
    public UUID getInsumoId() { return insumoId; }
    public BigDecimal getCantidadEstimada() { return cantidadEstimada; }
}
