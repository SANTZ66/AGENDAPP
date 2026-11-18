package com.fet.citas.agenda.dominio;

/**
 * Ciclo de vida de una cita en el alcance de esta entrega.
 * agendada -> completada  (se presto el servicio)
 * agendada -> cancelada   (se libero el cupo)
 */
public enum EstadoCita {
    AGENDADA,
    CANCELADA,
    COMPLETADA
}
