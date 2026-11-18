package com.fet.citas.operaciones.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Rastro de cada descuento de inventario, para poder auditarlo. */
@Entity
@Table(name = "movimiento_insumo", schema = "operaciones")
public class MovimientoInsumo {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "insumo_id", nullable = false)
    private UUID insumoId;

    @Column(name = "cita_id")
    private UUID citaId;

    @Column(nullable = false)
    private BigDecimal cantidad;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    protected MovimientoInsumo() {
    }

    public MovimientoInsumo(UUID insumoId, UUID citaId, BigDecimal cantidad) {
        this.insumoId = insumoId;
        this.citaId = citaId;
        this.cantidad = cantidad;
        this.creadoEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getInsumoId() { return insumoId; }
    public UUID getCitaId() { return citaId; }
    public BigDecimal getCantidad() { return cantidad; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
