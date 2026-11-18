// Piezas pequeñas que se repiten por toda la aplicacion.

import { useEffect, useRef } from 'react'

// --------------------------------------------------------------------
// Estado de una cita
// --------------------------------------------------------------------

const ESTADOS = {
  agendada: { texto: 'Agendada', tono: 'espera' },
  completada: { texto: 'Atendida', tono: 'hecho' },
  cancelada: { texto: 'Anulada', tono: 'anulado' }
}

/**
 * Un punto de color y una palabra. Se prefiere esto a una pastilla de
 * fondo lleno porque en una tabla larga el color de fondo repetido compite
 * con el contenido.
 *
 * Las palabras son las del salon, no las de la base de datos: la cita se
 * "atiende" y se "anula", aunque por dentro el campo diga completada y
 * cancelada.
 */
export function Estado({ valor }) {
  const e = ESTADOS[valor] || { texto: valor, tono: 'espera' }
  return (
    <span className={'estado estado--' + e.tono}>
      <span className="estado__punto" />
      {e.texto}
    </span>
  )
}

/** Marca de una cita agendada que la clienta ya confirmo. */
export function EtiquetaConfirmada() {
  return <span className="etiqueta-confirmada">Confirmada</span>
}

// --------------------------------------------------------------------
// Esmalte: el color que identifica cada servicio
// --------------------------------------------------------------------

// Una carta de colores de esmalte. El servicio siempre recibe el mismo
// color porque se calcula a partir de su nombre, asi que la clienta
// aprende a reconocerlo de un vistazo en la agenda.
const CARTA = [
  '#C2365E', '#D98032', '#7A5AA8', '#2F6E8F',
  '#B5466E', '#3F8C72', '#A8603C', '#5C6BBF'
]

export function colorDeServicio(nombre = '') {
  let suma = 0
  for (let i = 0; i < nombre.length; i++) suma = (suma * 31 + nombre.charCodeAt(i)) % 9973
  return CARTA[suma % CARTA.length]
}

/** La gota de color que precede al nombre de un servicio. */
export function Esmalte({ servicio, tam = 10 }) {
  return (
    <span className="esmalte"
          style={{ background: colorDeServicio(servicio), width: tam, height: tam }}
          aria-hidden="true" />
  )
}

// --------------------------------------------------------------------
// Ventana modal
// --------------------------------------------------------------------

/**
 * Sustituye a los campos que antes aparecian dentro de la fila de la tabla.
 * Poner un formulario dentro de una celda hacia saltar el ancho de las
 * columnas y dejaba la accion perdida entre el resto de filas.
 */
export function Modal({ titulo, apunte, onCerrar, children, pie }) {
  const caja = useRef(null)

  useEffect(() => {
    function alPulsar(e) { if (e.key === 'Escape') onCerrar() }
    document.addEventListener('keydown', alPulsar)
    caja.current?.focus()
    return () => document.removeEventListener('keydown', alPulsar)
  }, [onCerrar])

  return (
    <div className="velo" onMouseDown={e => { if (e.target === e.currentTarget) onCerrar() }}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={titulo}
           ref={caja} tabIndex={-1}>
        <header className="modal__cabecera">
          <h2>{titulo}</h2>
          {apunte && <p>{apunte}</p>}
        </header>
        <div className="modal__cuerpo">{children}</div>
        {pie && <footer className="modal__pie">{pie}</footer>}
      </div>
    </div>
  )
}

// --------------------------------------------------------------------
// Cifra destacada
// --------------------------------------------------------------------

export function Cifra({ rotulo, valor, pie, tono = 'normal' }) {
  return (
    <div className={'cifra cifra--' + tono}>
      <p className="cifra__rotulo">{rotulo}</p>
      <p className="cifra__valor">{valor}</p>
      {pie && <p className="cifra__pie">{pie}</p>}
    </div>
  )
}

// --------------------------------------------------------------------
// Pantalla vacia
// --------------------------------------------------------------------

/** Un hueco sin contenido es una invitacion a hacer algo, no un aviso. */
export function Vacio({ titulo, texto, accion }) {
  return (
    <div className="vacio">
      <p className="vacio__titulo">{titulo}</p>
      {texto && <p className="vacio__texto">{texto}</p>}
      {accion}
    </div>
  )
}
