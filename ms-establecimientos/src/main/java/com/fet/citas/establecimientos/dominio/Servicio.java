package com.fet.citas.establecimientos.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un servicio del catalogo, con su duracion y su precio.
 *
 * La duracion es lo que ms-agenda usa para calcular que cabe en una franja.
 * El precio es el que se congela en la cita al reservar: si el catalogo
 * cambia despues, las liquidaciones ya registradas no se alteran.
 */
@Entity
@Table(name = "servicio", schema = "establecimientos")
public class Servicio {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "establecimiento_id", nullable = false)
    private UUID establecimientoId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos;

    @Column(nullable = false)
    private BigDecimal precio;

    /** Cada cuantos dias suele repetirse este servicio (recurrencia). */
    @Column(name = "periodicidad_dias")
    private Integer periodicidadDias;

    protected Servicio() {
    }

    public Servicio(UUID establecimientoId, String nombre, int duracionMinutos,
                    BigDecimal precio, Integer periodicidadDias) {
        this.establecimientoId = establecimientoId;
        this.nombre = nombre;
        this.duracionMinutos = duracionMinutos;
        this.precio = precio;
        this.periodicidadDias = periodicidadDias;
    }

    public UUID getId() { return id; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public String getNombre() { return nombre; }
    public int getDuracionMinutos() { return duracionMinutos; }
    public BigDecimal getPrecio() { return precio; }
    public Integer getPeriodicidadDias() { return periodicidadDias; }
}
