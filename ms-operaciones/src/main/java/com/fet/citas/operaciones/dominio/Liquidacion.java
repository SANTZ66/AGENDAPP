package com.fet.citas.operaciones.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El ingreso registrado por un servicio prestado.
 *
 * citaId es UNIQUE: esa restriccion es la garantia de idempotencia. Si
 * Kafka reentrega el mismo evento, la segunda insercion falla y no se
 * duplica el ingreso (RNF-04).
 */
@Entity
@Table(name = "liquidacion", schema = "operaciones",
       uniqueConstraints = @UniqueConstraint(name = "liquidacion_unica_por_cita",
                                             columnNames = "cita_id"))
public class Liquidacion {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "cita_id", nullable = false)
    private UUID citaId;

    @Column(name = "establecimiento_id", nullable = false)
    private UUID establecimientoId;

    @Column(name = "profesional_id", nullable = false)
    private UUID profesionalId;

    @Column(name = "servicio_id", nullable = false)
    private UUID servicioId;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "fecha_servicio", nullable = false)
    private OffsetDateTime fechaServicio;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    protected Liquidacion() {
    }

    public Liquidacion(UUID citaId, UUID establecimientoId, UUID profesionalId,
                       UUID servicioId, BigDecimal valor, OffsetDateTime fechaServicio) {
        this.citaId = citaId;
        this.establecimientoId = establecimientoId;
        this.profesionalId = profesionalId;
        this.servicioId = servicioId;
        this.valor = valor == null ? BigDecimal.ZERO : valor;
        this.fechaServicio = fechaServicio;
        this.creadaEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCitaId() { return citaId; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public UUID getProfesionalId() { return profesionalId; }
    public UUID getServicioId() { return servicioId; }
    public BigDecimal getValor() { return valor; }
    public OffsetDateTime getFechaServicio() { return fechaServicio; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
}
