// La barra de navegacion.
//
// En pantalla ancha es una columna a la izquierda, siempre visible, con el
// salon arriba y el equipo abajo. En movil la misma lista se convierte en
// una barra de pestañas al pie, que es donde el pulgar llega sin esfuerzo.
// Las dos formas salen del mismo marcado: solo cambia el CSS.

import { NavLink, Link, useNavigate } from 'react-router-dom'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'
import { useConexion } from '../ganchos/useConexion.js'
import { useEnVivo } from '../ganchos/useEnVivo.jsx'
import { cerrar } from '../estado/sesion.js'
import {
  IconoHoy, IconoAgenda, IconoOperacion, IconoClientas
} from './Iconos.jsx'

const SECCIONES = [
  { a: '/negocio', texto: 'Hoy', Icono: IconoHoy, exacto: true },
  { a: '/negocio/agenda', texto: 'Agenda', Icono: IconoAgenda, vivo: true },
  { a: '/negocio/operacion', texto: 'Operación', Icono: IconoOperacion },
  { a: '/negocio/clientas', texto: 'Clientas', Icono: IconoClientas }
]

export default function Lateral() {
  const {
    establecimientos, profesionales,
    establecimientoId,
    profesionalId, setProfesionalId
  } = useEstablecimiento()

  const conectado = useConexion()
  const { cuenta } = useEnVivo()
  const navegar = useNavigate()
  const salon = establecimientos.find(e => e.id === establecimientoId)

  // Cierre voluntario: borra la sesion y lleva al login. Al desmontarse el
  // panel se detiene tambien la revision en vivo de reservas.
  function salir() {
    cerrar()
    navegar('/negocio/login', { replace: true })
  }

  return (
    <nav className="lateral">
      <div>
        <div className="lateral__marca">
          <span className="lateral__gota" aria-hidden="true" />
          <span className="lateral__nombre">AGENDAPP</span>
        </div>
        <p className="lateral__rol">Panel del establecimiento</p>
      </div>

      <ul className="lateral__lista">
        {SECCIONES.map(({ a, texto, Icono, exacto, vivo }) => (
          <li key={a}>
            <NavLink to={a} end={exacto} className="lateral__enlace">
              <Icono tam={19} />
              <span>{texto}</span>
              {vivo && cuenta > 0 && (
                <span className="lateral__nuevas" aria-label={cuenta + ' reservas nuevas'}>
                  {cuenta}
                </span>
              )}
            </NavLink>
          </li>
        ))}
      </ul>

      <div className="lateral__pie">
        {!conectado && (
          <p className="reconectando">
            <span className="reconectando__pulso" aria-hidden="true" />
            Reconectando…
          </p>
        )}

        {/* Aqui iba el selector de establecimiento. Ya no: la sesion fija el
            salon (ver RutaProtegida). El del profesional se mantiene. */}
        <label className="eleccion">
          <span>Agenda de</span>
          <select value={profesionalId}
                  onChange={e => setProfesionalId(e.target.value)}>
            {profesionales.length === 0
              ? <option value="">Sin asignar</option>
              : profesionales.map(p => (
                  <option key={p.id} value={p.id}>{p.nombre}</option>
                ))}
          </select>
        </label>

        <Link to="/" className="lateral__cambio">← Cambiar de versión</Link>
        <button type="button" className="lateral__cambio lateral__salir" onClick={salir}>
          Cerrar sesión
        </button>
      </div>
    </nav>
  )
}
