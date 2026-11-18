package com.fet.citas.agenda.eventos;

/**
 * Evento interno de Spring: "ocurrio este hecho, hay que publicarlo en este
 * topic". Lo emite CitaService dentro de la transaccion; lo recoge
 * PublicadorKafka DESPUES de que la transaccion confirma.
 *
 * Esta indireccion es la que garantiza lo que dice el documento: el evento
 * se publica unicamente si la reserva quedo realmente guardada. Si se
 * publicara dentro de la transaccion y esta fallara, los consumidores
 * habrian reaccionado a una cita que no existe.
 */
public record HechoDeCita(String topic, EventoCita evento) {

    public static final String AGENDADA = "citas.agendada";
    public static final String COMPLETADA = "citas.completada";
    public static final String CANCELADA = "citas.cancelada";
}
