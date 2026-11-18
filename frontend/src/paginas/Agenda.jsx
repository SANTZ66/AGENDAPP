import { useEffect, useState } from 'react'
import { ejecutar } from '../api/graphql.js'
import { useConsulta } from '../ganchos/useConsulta.js'
import { useAvisos } from '../ganchos/useAvisos.jsx'
import { AGENDA, SERVICIOS, CERRAR, CANCELAR } from '../api/consultas.js'
import { rangoDelAnio, soloDia, soloHora, pesos, etiquetaCliente } from '../api/fechas.js'
import { useEnVivo } from '../ganchos/useEnVivo.jsx'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'
import Panel from '../componentes/Panel.jsx'
import { Estado, EtiquetaConfirmada, Esmalte, Modal, Vacio } from '../componentes/Piezas.jsx'

const FILTROS = [
  { id: 'todas', texto: 'Todas' },
  { id: 'agendada', texto: 'Por atender' },
  { id: 'completada', texto: 'Atendidas' },
  { id: 'cancelada', texto: 'Anuladas' }
]

// La pantalla de trabajo del salon: ver las citas y cerrarlas.
//
// Cerrar una cita es la accion mas importante del sistema. Es la que
// publica el evento que liquida el ingreso, descuenta el insumo, pide la
// valoracion y programa el recontacto.
export default function Agenda() {
  const { establecimientoId, profesionalId } = useEstablecimiento()
  const { mostrar } = useAvisos()
  const { nuevas } = useEnVivo()
  const { desde, hasta } = rangoDelAnio()

  const listo = Boolean(profesionalId)
  const citas = useConsulta(
    AGENDA, { prof: profesionalId, desde, hasta }, { activo: listo, intervalo: 4000 }
  )
  const catalogo = useConsulta(
    SERVICIOS, { est: establecimientoId }, { activo: Boolean(establecimientoId) }
  )

  const [filtro, setFiltro] = useState('todas')
  const [cerrando, setCerrando] = useState(null)
  const [anulando, setAnulando] = useState(null)
  const [valor, setValor] = useState('')
  const [enviando, setEnviando] = useState(false)

  // El catalogo sirve para proponer el precio al cerrar, pero no es
  // imprescindible: si no llego, el valor se escribe a mano.
  const precios = {}
  ;(catalogo.datos.servicios || []).forEach(s => { precios[s.nombre] = s.precio })

  const lista = [...(citas.datos.agenda || [])].reverse()
  const visibles = filtro === 'todas' ? lista : lista.filter(c => c.estado === filtro)

  useEffect(() => {
    if (cerrando) setValor(String(precios[cerrando.servicio] ?? ''))
  }, [cerrando])

  async function confirmarCierre() {
    const numero = Number(valor)
    if (valor === '' || isNaN(numero) || numero < 0) {
      mostrar('Escribe cuánto se cobró por el servicio.', 'mal')
      return
    }
    setEnviando(true)
    const r = await ejecutar(
      CERRAR, { id: cerrando.id, valor: numero },
      'No pudimos cerrar la cita. Inténtalo de nuevo.'
    )
    setEnviando(false)

    if (!r.ok) { mostrar(r.mensaje, 'mal'); return }

    setCerrando(null)
    mostrar('Cita cerrada. Los ingresos y el inventario ya se actualizaron.')
    citas.refrescar()
  }

  async function confirmarAnulacion() {
    setEnviando(true)
    const r = await ejecutar(
      CANCELAR,
      { id: anulando.id, por: 'profesional', motivo: 'anulada desde la agenda' },
      'No pudimos anular la cita. Inténtalo de nuevo.'
    )
    setEnviando(false)

    if (!r.ok) { mostrar(r.mensaje, 'mal'); return }

    setAnulando(null)
    mostrar('Cita anulada.')
    citas.refrescar()
  }

  return (
    <>
      <header className="titular">
        <div>
          <p className="titular__fecha">Agenda</p>
          <h1 className="titular__texto">Todas las citas</h1>
        </div>
      </header>

      <div className="filtros" role="tablist" aria-label="Filtrar citas">
        {FILTROS.map(f => (
          <button key={f.id} role="tab" aria-selected={filtro === f.id}
                  className={'filtro' + (filtro === f.id ? ' filtro--activo' : '')}
                  onClick={() => setFiltro(f.id)}>
            {f.texto}
            <span className="filtro__cuenta">
              {f.id === 'todas' ? lista.length : lista.filter(c => c.estado === f.id).length}
            </span>
          </button>
        ))}
      </div>

      <Panel
        cargando={citas.cargandoPrimera && listo}
        sinInformacion={citas.vacios.length > 0}
        queEs="La agenda"
        desactualizado={citas.desactualizados.length > 0}
        actualizadoEn={citas.actualizadoEn}
        alReintentar={citas.refrescar}
        compacto>
        {visibles.length === 0
          ? <Vacio titulo="No hay citas aquí"
                   texto={filtro === 'todas'
                     ? 'Cuando un cliente reserve, la cita aparecerá aquí sola.'
                     : 'Prueba con otro filtro.'} />
          : (
            <ul className="citas">
              {visibles.map(c => (
                <li key={c.id}
                    className={'citas__fila' + (nuevas.has(c.id) ? ' citas__fila--nueva' : '')}>
                  <div className="citas__cuando">
                    <span className="citas__dia">{soloDia(c.inicio)}</span>
                    <span className="citas__hora">{soloHora(c.inicio)}</span>
                  </div>

                  <div className="citas__que">
                    <p className="citas__servicio">
                      <Esmalte servicio={c.servicio} />
                      {c.servicio}
                      {nuevas.has(c.id) && <span className="etiqueta-nueva">Nueva reserva</span>}
                      {c.estado === 'agendada' && c.confirmadaEn && <EtiquetaConfirmada />}
                      {c.duracionMinutos && (
                        <span className="citas__duracion">{c.duracionMinutos} min</span>
                      )}
                    </p>
                    <p className="citas__cliente">{etiquetaCliente(c.clienteId)}</p>
                  </div>

                  <div className="citas__estado"><Estado valor={c.estado} /></div>

                  <div className="citas__valor">
                    {c.valorCobrado ? pesos(c.valorCobrado) : ''}
                  </div>

                  <div className="citas__acciones">
                    {c.estado === 'agendada' && (
                      <>
                        <button className="boton boton--principal boton--chico"
                                onClick={() => setCerrando(c)}>
                          Cerrar cita
                        </button>
                        <button className="boton boton--suave boton--chico"
                                onClick={() => setAnulando(c)}>
                          Anular
                        </button>
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
      </Panel>

      {cerrando && (
        <Modal
          titulo="Cerrar la cita"
          apunte={`${cerrando.servicio} · ${soloDia(cerrando.inicio)} a las ${soloHora(cerrando.inicio)}`}
          onCerrar={() => setCerrando(null)}
          pie={
            <>
              <button className="boton boton--suave" onClick={() => setCerrando(null)}>
                Volver
              </button>
              <button className="boton boton--principal" onClick={confirmarCierre}
                      disabled={enviando}>
                {enviando ? 'Cerrando…' : 'Cerrar cita'}
              </button>
            </>
          }>
          <label className="campo">
            <span>¿Cuánto se cobró?</span>
            <input type="number" inputMode="numeric" value={valor} autoFocus
                   onChange={e => setValor(e.target.value)}
                   onKeyDown={e => { if (e.key === 'Enter') confirmarCierre() }} />
          </label>
          <p className="nota">
            Al cerrarla se descuenta el material usado y el cobro entra en los
            ingresos del día.
          </p>
        </Modal>
      )}

      {anulando && (
        <Modal
          titulo="¿Anular esta cita?"
          apunte={`${anulando.servicio} · ${soloDia(anulando.inicio)} a las ${soloHora(anulando.inicio)}`}
          onCerrar={() => setAnulando(null)}
          pie={
            <>
              <button className="boton boton--suave" onClick={() => setAnulando(null)}>
                Volver
              </button>
              <button className="boton boton--peligro" onClick={confirmarAnulacion}
                      disabled={enviando}>
                {enviando ? 'Anulando…' : 'Sí, anular'}
              </button>
            </>
          }>
          <p className="nota">
            La cita quedará marcada como anulada y se avisará a la clienta.
          </p>
        </Modal>
      )}
    </>
  )
}
