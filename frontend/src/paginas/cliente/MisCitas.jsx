import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ejecutar } from '../../api/graphql.js'
import { useConsulta } from '../../ganchos/useConsulta.js'
import { useAvisos } from '../../ganchos/useAvisos.jsx'
import { agendaDeTodos, CANCELAR, CONFIRMAR } from '../../api/consultas.js'
import { rangoDelAnio, soloDia, soloHora, pesos } from '../../api/fechas.js'
import { leerCliente } from '../../api/identidad.js'
import { avisarReserva } from '../../api/canal.js'
import { useEstablecimiento } from '../../contexto/Establecimiento.jsx'
import Panel from '../../componentes/Panel.jsx'
import { Estado, EtiquetaConfirmada, Esmalte, Modal, Vacio } from '../../componentes/Piezas.jsx'

// Las citas de esta persona. Se refrescan solas: cuando el establecimiento
// atiende o anula una, el estado cambia aquí sin recargar.
export default function MisCitas() {
  const { profesionales, salon } = useEstablecimiento()
  const { mostrar } = useAvisos()
  const [cliente] = useState(leerCliente)
  const [anulando, setAnulando] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [confirmando, setConfirmando] = useState(null)
  const { desde, hasta } = rangoDelAnio()

  const consulta = agendaDeTodos(profesionales)
  const q = useConsulta(
    consulta, { desde, hasta },
    { activo: consulta !== '', intervalo: 4000 }
  )

  const mias = []
  profesionales.forEach((p, i) => {
    ;(q.datos['p' + i] || [])
      .filter(c => c.clienteId === cliente.id)
      .forEach(c => mias.push({ ...c, profesional: p.nombre }))
  })
  mias.sort((a, b) => b.inicio.localeCompare(a.inicio))

  async function confirmar(c) {
    setConfirmando(c.id)
    const r = await ejecutar(
      CONFIRMAR, { id: c.id },
      'No pudimos confirmar tu cita. Inténtalo de nuevo.'
    )
    setConfirmando(null)
    if (!r.ok) { mostrar(r.mensaje, 'mal'); return }
    mostrar('Tu cita quedó confirmada.')
    avisarReserva({})
    q.refrescar()
  }

  async function anular() {
    setEnviando(true)
    const r = await ejecutar(
      CANCELAR, { id: anulando.id, por: 'cliente', motivo: 'anulada por el cliente' },
      'No pudimos anular la cita. Inténtalo de nuevo.'
    )
    setEnviando(false)
    if (!r.ok) { mostrar(r.mensaje, 'mal'); return }
    setAnulando(null)
    mostrar('Tu cita fue anulada.')
    avisarReserva({})
    q.refrescar()
  }

  return (
    <>
      <header className="cliente__titular">
        <h1>Mis citas</h1>
        <p>{salon ? `En ${salon.nombre}, a nombre de ${cliente.nombre}.` : ''}</p>
      </header>

      <Panel
        cargando={q.cargandoPrimera && consulta !== ''}
        sinInformacion={q.vacios.length > 0}
        queEs="Tus citas"
        desactualizado={q.desactualizados.length > 0}
        actualizadoEn={q.actualizadoEn}
        alReintentar={q.refrescar}
        compacto>
        {mias.length === 0
          ? <Vacio titulo="Todavía no tienes citas aquí"
                   texto="Cuando reserves una, aparecerá en esta lista."
                   accion={<Link to="/cliente" className="boton boton--principal">Reservar ahora</Link>} />
          : (
            <ul className="citas">
              {mias.map(c => (
                <li key={c.id} className="citas__fila citas__fila--cliente">
                  <div className="citas__cuando">
                    <span className="citas__dia">{soloDia(c.inicio)}</span>
                    <span className="citas__hora">{soloHora(c.inicio)}</span>
                  </div>
                  <div className="citas__que">
                    <p className="citas__servicio">
                      <Esmalte servicio={c.servicio} />
                      {c.servicio}
                    </p>
                    <p className="citas__cliente">con {c.profesional}</p>
                  </div>
                  <div className="citas__estado"><Estado valor={c.estado} /></div>
                  <div className="citas__valor">{c.valorCobrado ? pesos(c.valorCobrado) : ''}</div>
                  <div className="citas__acciones">
                    {c.estado === 'agendada' && (c.confirmadaEn
                      ? <EtiquetaConfirmada />
                      : (
                        <button className="boton boton--principal boton--chico"
                                onClick={() => confirmar(c)} disabled={confirmando === c.id}>
                          {confirmando === c.id ? 'Confirmando…' : 'Confirmar'}
                        </button>
                      ))}
                    {c.estado === 'agendada' && (
                      <button className="boton boton--suave boton--chico" onClick={() => setAnulando(c)}>
                        Anular
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
      </Panel>

      {anulando && (
        <Modal titulo="¿Anular tu cita?"
               apunte={`${anulando.servicio} · ${soloDia(anulando.inicio)} a las ${soloHora(anulando.inicio)}`}
               onCerrar={() => setAnulando(null)}
               pie={
                 <>
                   <button className="boton boton--suave" onClick={() => setAnulando(null)}>Volver</button>
                   <button className="boton boton--peligro" onClick={anular} disabled={enviando}>
                     {enviando ? 'Anulando…' : 'Sí, anular'}
                   </button>
                 </>
               }>
          <p className="nota">La hora quedará libre para otras personas.</p>
        </Modal>
      )}
    </>
  )
}
