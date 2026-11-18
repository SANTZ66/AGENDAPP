import { useEffect } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { ProveedorEstablecimiento, useEstablecimiento } from './contexto/Establecimiento.jsx'
import { ProveedorAvisos, useAvisos } from './ganchos/useAvisos.jsx'
import { consumirMotivoDeCierre, VENCIDA } from './estado/sesion.js'
import { Esqueleto, SinInformacion, SinConexion } from './componentes/Panel.jsx'
import { useConexion } from './ganchos/useConexion.js'
import { useSesion } from './ganchos/useSesion.js'
import Portada from './paginas/Portada.jsx'
import Login from './paginas/Login.jsx'
import VersionCliente from './paginas/VersionCliente.jsx'
import VersionNegocio from './paginas/VersionNegocio.jsx'

// La plataforma tiene dos versiones, una por tipo de persona:
//
//   /               portada: se elige quien eres
//   /cliente        quien reserva una cita (sin inicio de sesion)
//   /negocio        quien atiende en el establecimiento (exige sesion)
//   /negocio/login  inicio de sesion del establecimiento
export default function App() {
  return (
    <ProveedorAvisos>
      <ProveedorEstablecimiento>
        <Routes>
          <Route path="/" element={<Portada />} />
          <Route path="/cliente/*" element={<Guardia><VersionCliente /></Guardia>} />
          <Route path="/negocio/login" element={<Guardia><Login /></Guardia>} />
          <Route path="/negocio/*" element={
            <Guardia><RutaProtegida><VersionNegocio /></RutaProtegida></Guardia>
          } />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </ProveedorEstablecimiento>
    </ProveedorAvisos>
  )
}

// Sin sesion vigente no se monta nada del panel: ni sus pantallas ni la
// revision en vivo de reservas, que solo existe dentro de VersionNegocio. Se
// recuerda a donde iba la persona para devolverla ahi despues de entrar.
//
// Si la sesion termino porque vencio, se le explica con un aviso. Si ella
// misma pulso "Cerrar sesion", no hace falta decirle nada.
function RutaProtegida({ children }) {
  const sesion = useSesion()
  const ubicacion = useLocation()
  const { mostrar } = useAvisos()
  const { establecimientos, establecimientoId, setEstablecimientoId } = useEstablecimiento()

  useEffect(() => {
    if (sesion) return
    if (consumirMotivoDeCierre() === VENCIDA) {
      mostrar('Tu sesión venció. Inicia sesión de nuevo.', 'mal')
    }
  }, [sesion, mostrar])

  // El establecimiento lo fija la sesion, no se elige. Tras recargar la pagina
  // el contexto vuelve a escoger el primero de la lista, que puede ser otro:
  // se corrige ANTES de dibujar el panel para que ninguna consulta salga con
  // un establecimiento ajeno a la sesion.
  const salonDeLaSesion = sesion?.establecimientoId
  const desalineado = Boolean(salonDeLaSesion) && salonDeLaSesion !== establecimientoId
    && establecimientos.some(e => e.id === salonDeLaSesion)

  useEffect(() => {
    if (desalineado) setEstablecimientoId(salonDeLaSesion)
  }, [desalineado, salonDeLaSesion, setEstablecimientoId])

  if (!sesion) {
    return <Navigate to="/negocio/login" replace state={{ desde: ubicacion.pathname }} />
  }
  if (desalineado) return null
  return children
}

// Antes de mostrar cualquiera de las dos versiones hay que tener datos.
function Guardia({ children }) {
  const { arrancando, reintentar, establecimientos } = useEstablecimiento()
  const conectado = useConexion()

  // Mientras llega la primera respuesta se dibuja un armazon, no un texto de
  // "cargando" centrado. Asi la pantalla no da un salto cuando llegan los datos.
  if (arrancando) {
    return (
      <div className="app app--sola">
        <main className="tablero">
          <div className="esqueleto"><div className="esqueleto__linea" style={{ width: '30%', height: 28 }} /></div>
          <Esqueleto lineas={6} />
        </main>
      </div>
    )
  }

  // Dos ausencias que parecen la misma y no lo son. Sin conexion no hay app;
  // con conexion pero sin establecimientos, lo que falta son datos.
  if (establecimientos.length === 0) {
    return (
      <div className="app app--sola">
        <main className="tablero tablero--centrado">
          {conectado
            ? <SinInformacion queEs="La información de los establecimientos" alReintentar={reintentar} />
            : <SinConexion alReintentar={reintentar} />}
        </main>
      </div>
    )
  }

  return children
}
