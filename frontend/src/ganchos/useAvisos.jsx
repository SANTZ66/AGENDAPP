// Avisos flotantes: la confirmacion o el fallo de una accion del usuario.
//
// Van en una capa aparte, sobre la pagina, y se van solos. Antes estos
// mensajes se insertaban en el cuerpo de la pantalla, lo que empujaba el
// contenido hacia abajo justo cuando la persona iba a pulsar algo.

import { createContext, useCallback, useContext, useState } from 'react'

const Contexto = createContext(null)
const DURACION_MS = 5000
let contador = 0

export function ProveedorAvisos({ children }) {
  const [avisos, setAvisos] = useState([])

  const quitar = useCallback(id => {
    setAvisos(lista => lista.filter(a => a.id !== id))
  }, [])

  const mostrar = useCallback((texto, tono = 'ok') => {
    const id = ++contador
    setAvisos(lista => [...lista, { id, texto, tono }])
    setTimeout(() => quitar(id), DURACION_MS)
  }, [quitar])

  return (
    <Contexto.Provider value={{ mostrar }}>
      {children}
      <div className="avisos" role="status" aria-live="polite">
        {avisos.map(a => (
          <div key={a.id} className={'aviso aviso--' + a.tono}>
            <span className="aviso__icono" aria-hidden="true">
              {a.tono === 'ok' ? <IconoCheck /> : <IconoAlerta />}
            </span>
            <p>{a.texto}</p>
            <button className="aviso__cerrar" onClick={() => quitar(a.id)}
                    aria-label="Cerrar aviso">
              <IconoEquis />
            </button>
          </div>
        ))}
      </div>
    </Contexto.Provider>
  )
}

export function useAvisos() {
  const valor = useContext(Contexto)
  if (!valor) throw new Error('useAvisos necesita estar dentro de ProveedorAvisos')
  return valor
}

function IconoCheck() {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16" fill="none"
         stroke="currentColor" strokeWidth="2" strokeLinecap="round"
         strokeLinejoin="round">
      <path d="M3 8.5 6.5 12 13 4.5" />
    </svg>
  )
}

function IconoAlerta() {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16" fill="none"
         stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M8 4.5v4.2" />
      <circle cx="8" cy="11.6" r="0.9" fill="currentColor" stroke="none" />
      <circle cx="8" cy="8" r="6.3" />
    </svg>
  )
}

function IconoEquis() {
  return (
    <svg viewBox="0 0 16 16" width="14" height="14" fill="none"
         stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
      <path d="M4.5 4.5l7 7M11.5 4.5l-7 7" />
    </svg>
  )
}
