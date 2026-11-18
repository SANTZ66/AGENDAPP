package com.fet.citas.operaciones.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Un material que se consume al prestar un servicio. */
@Entity
@Table(name = "insumo", schema = "operaciones")
public class Insumo {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "establecimiento_id", nullable = false)
    private UUID establecimientoId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 20)
    private String unidad;

    @Column(nullable = false)
    private BigDecimal existencia;

    @Column(name = "umbral_minimo", nullable = false)
    private BigDecimal umbralMinimo;

    protected Insumo() {
    }

    public Insumo(UUID establecimientoId, String nombre, String unidad,
                  BigDecimal existencia, BigDecimal umbralMinimo) {
        this.establecimientoId = establecimientoId;
        this.nombre = nombre;
        this.unidad = unidad;
        this.existencia = existencia;
        this.umbralMinimo = umbralMinimo;
    }

    /** Descuenta lo consumido. No baja de cero. */
    public void descontar(BigDecimal cantidad) {
        BigDecimal nueva = this.existencia.subtract(cantidad);
        this.existencia = nueva.signum() < 0 ? BigDecimal.ZERO : nueva;
    }

    public void ajustar(BigDecimal existencia, BigDecimal umbralMinimo) {
        if (existencia != null) this.existencia = existencia;
        if (umbralMinimo != null) this.umbralMinimo = umbralMinimo;
    }

    /** Verdadero cuando toca reponer. */
    public boolean enUmbral() {
        return existencia.compareTo(umbralMinimo) <= 0;
    }

    public UUID getId() { return id; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public String getNombre() { return nombre; }
    public String getUnidad() { return unidad; }
    public BigDecimal getExistencia() { return existencia; }
    public BigDecimal getUmbralMinimo() { return umbralMinimo; }
}
