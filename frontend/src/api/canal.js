// Aviso instantaneo entre ventanas del mismo navegador.
//
// Cuando la clienta reserva en una pestaña, la pestaña del establecimiento
// se entera al momento y refresca sus datos. Si no hay BroadcastChannel (o
// la reserva se hizo desde otro equipo) no pasa nada grave: la version del
// establecimiento tambien consulta cada pocos segundos.

let canal = null
try { canal = new BroadcastChannel('plataforma-citas') } catch { canal = null }

export function avisarReserva(detalle = {}) {
  try { canal?.postMessage({ tipo: 'reserva', ...detalle }) } catch { /* nada */ }
}

/** Suscribe a las reservas hechas en otras ventanas. Devuelve la baja. */
export function alReservar(oyente) {
  if (!canal) return () => {}
  const manejar = e => { if (e.data?.tipo === 'reserva') oyente(e.data) }
  canal.addEventListener('message', manejar)
  return () => canal.removeEventListener('message', manejar)
}
