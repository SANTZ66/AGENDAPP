package com.fet.citas.relacion.dominio;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El historial de un cliente en un establecimiento.
 *
 * Es lo que ataca el problema P6: hoy el negocio no sabe quien dejo de
 * volver. Este registro se construye solo, a partir de los eventos, sin
 * que nadie lo alimente a mano.
 */
@Entity
@Table(name = "cliente_recurrencia", schema = "relacion_cliente",
       uniqueConstraints = @UniqueConstraint(name = "cliente_unico_por_establecimiento",
                                             columnNames = {"establecimiento_id", "cliente_id"}))
public class ClienteRecurrencia {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "establecimiento_id", nullable = false)
    private UUID establecimientoId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "ultima_visita")
    private OffsetDateTime ultimaVisita;

    @Column(name = "proximo_contacto")
    private OffsetDateTime proximoContacto;

    @Column(name = "total_visitas", nullable = false)
    private int totalVisitas;

    @Column(name = "total_cancelaciones", nullable = false)
    private int totalCancelaciones;

    protected ClienteRecurrencia() {
    }

    public ClienteRecurrencia(UUID establecimientoId, UUID clienteId) {
        this.establecimientoId = establecimientoId;
        this.clienteId = clienteId;
        this.totalVisitas = 0;
        this.totalCancelaciones = 0;
    }

    /** Suma una visita y programa el recontacto segun la periodicidad. */
    public void registrarVisita(OffsetDateTime cuando, int periodicidadDias) {
        this.ultimaVisita = cuando;
        this.totalVisitas++;
        this.proximoContacto = cuando.plusDays(periodicidadDias);
    }

    public void registrarCancelacion() {
        this.totalCancelaciones++;
    }

    public UUID getId() { return id; }
    public UUID getEstablecimientoId() { return establecimientoId; }
    public UUID getClienteId() { return clienteId; }
    public OffsetDateTime getUltimaVisita() { return ultimaVisita; }
    public OffsetDateTime getProximoContacto() { return proximoContacto; }
    public int getTotalVisitas() { return totalVisitas; }
    public int getTotalCancelaciones() { return totalCancelaciones; }
}
