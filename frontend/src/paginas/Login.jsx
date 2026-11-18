import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ejecutar } from '../api/graphql.js'
import { LOGIN } from '../api/consultas.js'
import { iniciar } from '../estado/sesion.js'
import { useSesion } from '../ganchos/useSesion.js'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'

// Inicio de sesion del establecimiento. Es la unica puerta a /negocio; la
// version cliente no pasa por aqui.
//
// El texto del error de credenciales es propio de esta pantalla. No se deja a
// humanizar(): sus reglas lo confundirian con otros errores, y una caida del
// servicio no debe leerse como "clave incorrecta".
const CREDENCIALES_MALAS = 'Correo o clave incorrectos.'

export default function Login() {
  const sesion = useSesion()
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const { setEstablecimientoId } = useEstablecimiento()

  const [correo, setCorreo] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  // A donde volver: la pantalla que se intento abrir, o el inicio del panel.
  const destino = ubicacion.state?.desde || '/negocio'

  // Quien ya tiene sesion no necesita ver el formulario.
  if (sesion) return <Navigate to={destino} replace />

  async function entrar(e) {
    e.preventDefault()
    setError('')
    setEnviando(true)

    const r = await ejecutar(
      LOGIN, { correo: correo.trim(), clave },
      'No pudimos iniciar sesión. Inténtalo de nuevo.'
    )

    if (!r.ok || !r.datos?.login) {
      setEnviando(false)
      setError(r.codigo === 'UNAUTHORIZED' ? CREDENCIALES_MALAS : r.mensaje)
      return
    }

    // El establecimiento sale de la sesion, ya no se elige a mano.
    setEstablecimientoId(r.datos.login.establecimientoId)
    iniciar(r.datos.login)
    navegar(destino, { replace: true })
  }

  return (
    <div className="portada">
      <header className="portada__cabecera">
        <span className="lateral__gota" aria-hidden="true" />
        <h1>Panel del establecimiento</h1>
        <p>Inicia sesión para continuar.</p>
      </header>

      <form className="opcion acceso" onSubmit={entrar}>
        <label className="campo">
          <span>Correo</span>
          <input type="email" value={correo} onChange={e => setCorreo(e.target.value)}
                 autoComplete="username" autoFocus required />
        </label>

        <label className="campo">
          <span>Clave</span>
          <input type="password" value={clave} onChange={e => setClave(e.target.value)}
                 autoComplete="current-password" required />
        </label>

        {error && <p className="acceso__error" role="alert">{error}</p>}

        <button type="submit" className="boton boton--principal" disabled={enviando}>
          {enviando ? 'Entrando…' : 'Entrar'}
        </button>

        <Link to="/" className="acceso__volver">← Cambiar de versión</Link>
      </form>
    </div>
  )
}
