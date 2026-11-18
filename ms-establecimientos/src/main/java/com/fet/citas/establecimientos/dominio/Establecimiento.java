package com.fet.citas.establecimientos.dominio;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un negocio que usa la plataforma. Es el inquilino: todos los datos del
 * sistema cuelgan de un establecimiento y nunca se mezclan entre ellos.
 */
@Entity
@Table(name = "establecimiento", schema = "establecimientos")
public class Establecimiento {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    /** Horas que tiene el cliente para confirmar antes de perder el cupo. */
    @Column(name = "ventana_confirmacion_horas", nullable = false)
    private int ventanaConfirmacionHoras;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    protected Establecimiento() {
    }

    public Establecimiento(String nombre, String telefono, int ventanaConfirmacionHoras) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.ventanaConfirmacionHoras = ventanaConfirmacionHoras;
        this.creadoEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public int getVentanaConfirmacionHoras() { return ventanaConfirmacionHoras; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
