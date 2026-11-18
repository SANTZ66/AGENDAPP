package com.fet.citas.simulador;

import java.util.UUID;

/**
 * Los identificadores que el simulador necesita para operar: se crean una
 * sola vez al arrancar y se reutilizan en cada cita generada.
 */
public class Escenario {

    private UUID establecimientoId;
    private UUID profesionalId;
    private UUID servicioId;
    private UUID insumoId;
    private boolean listo;

    public synchronized void completar(UUID establecimientoId, UUID profesionalId,
                                       UUID servicioId, UUID insumoId) {
        this.establecimientoId = establecimientoId;
        this.profesionalId = profesionalId;
        this.servicioId = servicioId;
        this.insumoId = insumoId;
        this.listo = true;
    }

    public synchronized boolean estaListo() { return listo; }
    public synchronized UUID establecimientoId() { return establecimientoId; }
    public synchronized UUID profesionalId() { return profesionalId; }
    public synchronized UUID servicioId() { return servicioId; }
    public synchronized UUID insumoId() { return insumoId; }
}
