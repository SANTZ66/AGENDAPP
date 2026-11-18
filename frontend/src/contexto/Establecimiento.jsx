import { createContext, useContext, useEffect, useState } from 'react'
import { useConsulta } from '../ganchos/useConsulta.js'
import { ESTABLECIMIENTOS, PROFESIONALES } from '../api/consultas.js'

// Casi todas las pantallas necesitan saber en que salon y con que
// profesional se esta trabajando. Pasarlo de componente en componente seria
// repetitivo, asi que vive en un contexto que cualquiera puede leer.
//
// Nota: el sistema todavia no tiene inicio de sesion. Con autenticacion, el
// salon saldria del usuario que entro y no habria que elegirlo.

const Contexto = createContext(null)

export function ProveedorEstablecimiento({ children }) {
  const [establecimientoId, setEstablecimientoId] = useState('')
  const [profesionalId, setProfesionalId] = useState('')

  // Reintenta sola si el servicio del catalogo no esta disponible, asi que
  // arrancar la app antes que el backend ya no obliga a recargar la pagina.
  const salones = useConsulta(ESTABLECIMIENTOS, {})
  const equipo = useConsulta(
    PROFESIONALES,
    { est: establecimientoId },
    { activo: Boolean(establecimientoId) }
  )

  const establecimientos = salones.datos.establecimientos || []
  const profesionales = equipo.datos.profesionales || []

  // Se elige el primero en cuanto llega la lista, y solo si no hay otro ya
  // elegido: asi un refresco posterior no deshace la eleccion del usuario.
  useEffect(() => {
    if (!establecimientoId && establecimientos.length > 0) {
      setEstablecimientoId(establecimientos[0].id)
    }
  }, [establecimientos, establecimientoId])

  useEffect(() => {
    if (profesionales.length === 0) return
    const sigueValido = profesionales.some(p => p.id === profesionalId)
    if (!sigueValido) setProfesionalId(profesionales[0].id)
  }, [profesionales, profesionalId])

  // Cambiar de salon invalida el profesional del salon anterior.
  function elegirSalon(id) {
    setEstablecimientoId(id)
    setProfesionalId('')
  }

  const valor = {
    establecimientos,
    profesionales,
    establecimientoId,
    setEstablecimientoId: elegirSalon,
    profesionalId,
    setProfesionalId,
    arrancando: salones.cargandoPrimera,
    sinCatalogo: salones.vacios.length > 0 && establecimientos.length === 0,
    reintentar: salones.refrescar,
    profesional: profesionales.find(p => p.id === profesionalId) || null,
    salon: establecimientos.find(e => e.id === establecimientoId) || null
  }

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

/** Atajo para leer el contexto desde cualquier pantalla. */
export function useEstablecimiento() {
  const valor = useContext(Contexto)
  if (!valor) {
    throw new Error('useEstablecimiento debe usarse dentro de ProveedorEstablecimiento')
  }
  return valor
}
