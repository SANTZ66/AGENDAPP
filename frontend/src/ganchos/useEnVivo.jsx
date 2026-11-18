// Las reservas nuevas, en vivo, para la version del establecimiento.
//
// Cada pocos segundos pide la agenda de todo el equipo y compara con lo que
// ya habia visto. Lo que aparece de mas es una reserva nueva: se muestra un
// aviso y la cita queda marcada como "Nueva" durante un rato en la agenda y
// en la jornada del dia.
//
// La primera lectura solo sirve para saber que ya existia; no avisa de nada.

import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useConsulta } from './useConsulta.js'
import { useAvisos } from './useAvisos.jsx'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'
import { agendaDeTodos } from '../api/consultas.js'
import { rangoDelAnio, soloDia, soloHora, etiquetaCliente } from '../api/fechas.js'

const REVISION_MS = 4000
const VIGENCIA_MS = 30000

const Contexto = createContext({ nuevas: new Set() })

export function ProveedorEnVivo({ children }) {
  const { establecimientoId } = useEstablecimiento()
  // La llave reinicia lo "ya visto" al cambiar de establecimiento.
  return <Vigia key={establecimientoId}>{children}</Vigia>
}

function Vigia({ children }) {
  const { profesionales } = useEstablecimiento()
  const { mostrar } = useAvisos()
  const { desde, hasta } = rangoDelAnio()

  const consulta = useMemo(() => agendaDeTodos(profesionales), [profesionales])
  const { datos, actualizadoEn } = useConsulta(
    consulta, { desde, hasta },
    { activo: consulta !== '', intervalo: REVISION_MS }
  )

  const vistos = useRef(null)
  const [nuevas, setNuevas] = useState(() => new Set())

  useEffect(() => {
    if (!actualizadoEn) return

    const todas = []
    profesionales.forEach((p, i) => {
      ;(datos['p' + i] || []).forEach(c => todas.push({ ...c, profesional: p.nombre }))
    })

    if (vistos.current === null) {
      vistos.current = new Set(todas.map(c => c.id))
      return
    }

    const reservas = todas.filter(c => !vistos.current.has(c.id))
    reservas.forEach(c => vistos.current.add(c.id))

    const agendadas = reservas.filter(c => c.estado === 'agendada')
    if (agendadas.length === 0) return

    setNuevas(previo => new Set([...previo, ...agendadas.map(c => c.id)]))
    agendadas.forEach(c => mostrar(
      `Nueva reserva: ${etiquetaCliente(c.clienteId)} · ${c.servicio} con ${c.profesional} · ` +
      `${soloDia(c.inicio)} ${soloHora(c.inicio)}`
    ))
    setTimeout(() => {
      setNuevas(previo => {
        const resto = new Set(previo)
        agendadas.forEach(c => resto.delete(c.id))
        return resto
      })
    }, VIGENCIA_MS)
  }, [actualizadoEn])

  return <Contexto.Provider value={{ nuevas }}>{children}</Contexto.Provider>
}

/** Las citas que acaban de llegar (ids) y cuantas son. */
export function useEnVivo() {
  const { nuevas } = useContext(Contexto)
  return { nuevas, cuenta: nuevas.size }
}
