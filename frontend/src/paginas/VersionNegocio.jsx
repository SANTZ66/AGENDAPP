import { Routes, Route, Navigate } from 'react-router-dom'
import { ProveedorEnVivo } from '../ganchos/useEnVivo.jsx'
import Lateral from '../componentes/Lateral.jsx'
import Hoy from './Hoy.jsx'
import Agenda from './Agenda.jsx'
import Operacion from './Operacion.jsx'
import Clientas from './Clientas.jsx'

// La version del establecimiento: panel de trabajo con barra lateral.
// Aqui no se reserva; aqui se ve llegar lo que reservan los clientes.
export default function VersionNegocio() {
  return (
    <ProveedorEnVivo>
      <div className="app">
        <Lateral />
        <main className="tablero">
          <Routes>
            <Route index element={<Hoy />} />
            <Route path="agenda" element={<Agenda />} />
            <Route path="operacion" element={<Operacion />} />
            <Route path="clientas" element={<Clientas />} />
            <Route path="*" element={<Navigate to="/negocio" replace />} />
          </Routes>
        </main>
      </div>
    </ProveedorEnVivo>
  )
}
