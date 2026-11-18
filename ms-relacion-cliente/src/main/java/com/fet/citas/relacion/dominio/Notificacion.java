package com.fet.citas.relacion.dominio;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un aviso que el sistema decidio enviar.
 *
 * En el MVP la entrega se REGISTRA, no se envia de verdad: integrar un
 * canal real (WhatsApp Business API) exige una cuenta aprobada y tiene
 * costo, y esta declarado fuera de alcance en el riesgo R5 del documento.
 * El canal queda abstraido para poder conectarlo despues sin tocar nada
 * mas de este servicio.
 */
@Entity
@Table(name = "notificacion", schema = "relacion_cliente",
       uniqueConstraints = @UniqueConstraint(name = "notificacion_unica",
                                             columnNames = {"cita_id", "tipo"}))
public class Notificacion {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "establecimiento_id", nullable = false)
    private UUID establecimientoId;

    @Column(name = "cita_id")
    private UUID citaId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String canal;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(nullable = false, length = 500)
    private String cuerpo;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    protected Notificacion() {
    }

    public Notificacion(UUID establecimientoId, UUID citaId, String tipo,
                        String asunto, String cuerpo) {
        this.establecimientoId = establecimientoId;
        this.citaId = citaId;
        this.tipo = tipo;
        this.canal = "simulado";
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.estado = "registrada";
        this.creadaEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public UUID getCitaId() { return citaId; }
    public String getTipo() { return tipo; }
    public String getCanal() { return canal; }
    public String getAsunto() { return asunto; }
    public String getCuerpo() { return cuerpo; }
    public String getEstado() { return estado; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
}
