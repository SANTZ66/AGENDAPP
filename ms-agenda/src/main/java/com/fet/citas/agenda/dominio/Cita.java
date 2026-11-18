package com.fet.citas.agenda.dominio;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una cita agendada entre un cliente y un profesional.
 *
 * La restriccion UNIQUE(profesional_id, inicio) es el corazon del control
 * de concurrencia (RNF-06): si dos personas intentan reservar el mismo
 * profesional a la misma hora, la base de datos rechaza la segunda
 * insercion y la aplicacion traduce ese fallo a un HTTP 409. No hay locks
 * en codigo ni coordinacion distribuida: la BD resuelve la carrera.
 */
@Entity
@Table(
    name = "cita",
    schema = "agenda",
    uniqueConstraints = @UniqueConstraint(
        name = "cupo_unico",
        columnNames = {"profesional_id", "inicio"}
    )
)
public class Cita {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "establecimiento_id", nullable = false)
    private UUID establecimientoId;

    @Column(name = "profesional_id", nullable = false)
    private UUID profesionalId;

    @Column(name = "servicio_id", nullable = false)
    private UUID servicioId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(nullable = false)
    private OffsetDateTime inicio;

    @Column(nullable = false)
    private OffsetDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCita estado;

    @Column(name = "valor_cobrado")
    private BigDecimal valorCobrado;

    @Column(name = "cancelada_por", length = 20)
    private String canceladaPor;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    /**
     * Cuando la clienta confirmo que asistira. Es NULL mientras no lo haga.
     * No es un estado nuevo: la cita sigue AGENDADA, y por eso cerrar() y
     * cancelar() no cambian.
     */
    @Column(name = "confirmada_en")
    private OffsetDateTime confirmadaEn;

    /** JPA exige un constructor sin argumentos. */
    protected Cita() {
    }

    public Cita(UUID establecimientoId, UUID profesionalId, UUID servicioId,
                UUID clienteId, OffsetDateTime inicio, OffsetDateTime fin) {
        this.establecimientoId = establecimientoId;
        this.profesionalId = profesionalId;
        this.servicioId = servicioId;
        this.clienteId = clienteId;
        this.inicio = inicio;
        this.fin = fin;
        this.estado = EstadoCita.AGENDADA;
        this.creadaEn = OffsetDateTime.now();
    }

    /** Marca la cita como completada tras prestar el servicio. */
    public void cerrar(BigDecimal valorCobrado) {
        this.estado = EstadoCita.COMPLETADA;
        this.valorCobrado = valorCobrado;
    }

    /**
     * La clienta confirma su asistencia. Es idempotente: si ya estaba
     * confirmada no se toca la fecha original.
     */
    public void confirmar() {
        if (this.confirmadaEn == null) {
            // Microsegundos, que es lo que guarda PostgreSQL. Con mas
            // precision, la primera respuesta traeria una fecha distinta
            // (en texto) a la que se lee despues de la base de datos.
            this.confirmadaEn = OffsetDateTime.now()
                    .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }

    /** Cancela la cita y libera el cupo. */
    public void cancelar(String canceladaPor, String motivo) {
        this.estado = EstadoCita.CANCELADA;
        this.canceladaPor = canceladaPor;
        this.motivoCancelacion = motivo;
    }

    // --- getters ---

    public UUID getId() { return id; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public UUID getProfesionalId() { return profesionalId; }
    public UUID getServicioId() { return servicioId; }
    public UUID getClienteId() { return clienteId; }
    public OffsetDateTime getInicio() { return inicio; }
    public OffsetDateTime getFin() { return fin; }
    public EstadoCita getEstado() { return estado; }
    public BigDecimal getValorCobrado() { return valorCobrado; }
    public String getCanceladaPor() { return canceladaPor; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
    public OffsetDateTime getConfirmadaEn() { return confirmadaEn; }
}
