package com.fet.citas.establecimientos.dominio;

import jakarta.persistence.*;

import java.util.UUID;

/** Quien presta el servicio. Un establecimiento puede tener varios. */
@Entity
@Table(name = "profesional", schema = "establecimientos")
public class Profesional {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "establecimiento_id", nullable = false)
    private UUID establecimientoId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    private boolean activo;

    protected Profesional() {
    }

    public Profesional(UUID establecimientoId, String nombre) {
        this.establecimientoId = establecimientoId;
        this.nombre = nombre;
        this.activo = true;
    }

    public UUID getId() { return id; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public String getNombre() { return nombre; }
    public boolean isActivo() { return activo; }
}
