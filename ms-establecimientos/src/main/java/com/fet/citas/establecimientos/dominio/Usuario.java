package com.fet.citas.establecimientos.dominio;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persona que entra al panel del establecimiento. Cada usuario pertenece a un
 * unico establecimiento. La clave se guarda siempre con BCrypt, nunca en claro.
 */
@Entity
@Table(name = "usuario", schema = "establecimientos")
public class Usuario {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "establecimiento_id", nullable = false, columnDefinition = "uuid")
    private UUID establecimientoId;

    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String rol;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    protected Usuario() {
    }

    public Usuario(UUID establecimientoId, String correo, String claveHash,
                   String nombre, String rol) {
        this.establecimientoId = establecimientoId;
        this.correo = correo.trim().toLowerCase();
        this.claveHash = claveHash;
        this.nombre = nombre;
        this.rol = rol;
        this.activo = true;
        this.creadoEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public String getCorreo() { return correo; }
    public String getClaveHash() { return claveHash; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
