import { useState } from 'react'
import { NavLink, Link, Routes, Route, Navigate } from 'react-router-dom'
import { leerCliente } from '../api/identidad.js'
import ReservarCliente from './cliente/ReservarCliente.jsx'
import MisCitas from './cliente/MisCitas.jsx'

// La version del cliente: sin barra lateral, una pagina limpia y guiada.
export default function VersionCliente() {
  const [cliente] = useState(leerCliente)

  return (
    <div className="cliente">
      <header className="cliente__barra">
        <Link to="/cliente" className="cliente__marca">
          <span className="lateral__gota" aria-hidden="true" />
          <span>Reserva tu cita</span>
        </Link>

        <nav className="cliente__nav">
          <NavLink to="/cliente" end>Reservar</NavLink>
          <NavLink to="/cliente/mis-citas">Mis citas</NavLink>
        </nav>

        <div className="cliente__yo">
          <span className="cliente__avatar" aria-hidden="true">
            {cliente.nombre.trim().slice(0, 1).toUpperCase() || '?'}
          </span>
          <Link to="/" className="cliente__salir">Cambiar de versión</Link>
        </div>
      </header>

      <main className="cliente__cuerpo">
        <Routes>
          <Route index element={<ReservarCliente />} />
          <Route path="mis-citas" element={<MisCitas />} />
          <Route path="*" element={<Navigate to="/cliente" replace />} />
        </Routes>
      </main>
    </div>
  )
}
