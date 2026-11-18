import { useState } from 'react'
import { ejecutar } from '../api/graphql.js'
import { useConsulta } from '../ganchos/useConsulta.js'
import { useAvisos } from '../ganchos/useAvisos.jsx'
import { SERVICIOS, DISPONIBILIDAD, RESERVAR } from '../api/consultas.js'
import { soloHora, aFechaSimple, pesos } from '../api/fechas.js'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'
import Panel from '../componentes/Panel.jsx'
import { Esmalte, Modal, Vacio } from '../componentes/Piezas.jsx'

// Pedir una cita, en tres pasos: el servicio, el dia y la hora.
//
// Ataca el problema P2 del documento. Hoy la disponibilidad del salon se
// publica en estados de WhatsApp que caducan a las 24 horas, asi que quien
// no mira a tiempo no se entera de que habia cupo. Aqui se consulta cuando
// a uno le quede bien.
export default function Reservar() {
  const { establecimientoId, profesionalId, profesional } = useEstablecimiento()
  const { mostrar } = useAvisos()

  const [servicioId, setServicioId] = useState('')
  const [fecha, setFecha] = useState(() => {
    const manana = new Date()
    manana.setDate(manana.getDate() + 1)
    return aFechaSimple(manana)
  })
  const [confirmando, setConfirmando] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [clienteId, setClienteId] = useState(() => crypto.randomUUID())

  const catalogo = useConsulta(
    SERVICIOS, { est: establecimientoId }, { activo: Boolean(establecimientoId) }
  )
  const servicios = catalogo.datos.servicios || []

  // El primero del catalogo queda elegido hasta que la persona toque otro.
  const elegido = servicios.find(s => s.id === servicioId) || servicios[0] || null

  const puedeBuscar = Boolean(profesionalId && elegido && fecha)
  const cupos = useConsulta(
    DISPONIBILIDAD,
    { prof: profesionalId, serv: elegido?.id || '', fecha },
    { activo: puedeBuscar }
  )
  const franjas = cupos.datos.disponibilidad || []

  async function reservar() {
    setEnviando(true)
    const r = await ejecutar(
      RESERVAR,
      {
        est: establecimientoId,
        prof: profesionalId,
        serv: elegido.id,
        cli: clienteId,
        inicio: confirmando.inicio,
        fin: confirmando.fin
      },
      'No pudimos reservar esa hora. Inténtalo de nuevo.'
    )
    setEnviando(false)

    if (!r.ok) {
      // El 409 de la base de datos llega aquí ya traducido a una frase.
      mostrar(r.mensaje, 'mal')
      setConfirmando(null)
      cupos.refrescar()
      return
    }

    mostrar('Cita reservada para las ' + soloHora(r.datos.reservarCita.inicio) + '.')
    setConfirmando(null)
    setClienteId(crypto.randomUUID())
    cupos.refrescar()
  }

  return (
    <>
      <header className="titular">
        <div>
          <p className="titular__fecha">Reservar</p>
          <h1 className="titular__texto">Buscar una hora libre</h1>
        </div>
      </header>

      <Panel
        titulo="¿Qué servicio?"
        cargando={catalogo.cargandoPrimera}
        sinInformacion={catalogo.vacios.length > 0}
        queEs="El catálogo de servicios"
        desactualizado={catalogo.desactualizados.length > 0}
        actualizadoEn={catalogo.actualizadoEn}
        alReintentar={catalogo.refrescar}>
        {servicios.length === 0
          ? <Vacio titulo="No hay servicios en el catálogo"
                   texto="Agrega al menos uno para poder reservar." />
          : (
            <div className="servicios">
              {servicios.map(s => (
                <button key={s.id}
                        className={'servicio' + (elegido?.id === s.id ? ' servicio--elegido' : '')}
                        onClick={() => setServicioId(s.id)}
                        aria-pressed={elegido?.id === s.id}>
                  <Esmalte servicio={s.nombre} tam={12} />
                  <span className="servicio__nombre">{s.nombre}</span>
                  <span className="servicio__meta">
                    {s.duracionMinutos} min · {pesos(s.precio)}
                  </span>
                </button>
              ))}
            </div>
          )}
      </Panel>

      <Panel titulo="¿Qué día?">
        <div className="fila-fecha">
          <label className="campo campo--corto">
            <span>Fecha</span>
            <input type="date" value={fecha} min={aFechaSimple(new Date())}
                   onChange={e => setFecha(e.target.value)} />
          </label>
          <div className="atajos">
            <button className="boton boton--suave boton--chico"
                    onClick={() => setFecha(aFechaSimple(new Date()))}>
              Hoy
            </button>
            <button className="boton boton--suave boton--chico"
                    onClick={() => setFecha(aFechaSimple(enDias(1)))}>
              Mañana
            </button>
            <button className="boton boton--suave boton--chico"
                    onClick={() => setFecha(aFechaSimple(enDias(7)))}>
              En una semana
            </button>
          </div>
        </div>
      </Panel>

      <Panel
        titulo="Horas libres"
        apunte={elegido && profesional
          ? `${elegido.nombre} con ${profesional.nombre}`
          : 'Elige un servicio y quién atiende'}
        cargando={cupos.cargandoPrimera && puedeBuscar}
        sinInformacion={cupos.vacios.length > 0}
        queEs="La disponibilidad"
        desactualizado={cupos.desactualizados.length > 0}
        actualizadoEn={cupos.actualizadoEn}
        alReintentar={cupos.refrescar}>
        {!puedeBuscar
          ? <Vacio titulo="Falta elegir algo"
                   texto="Selecciona un servicio y quién atiende en la barra lateral." />
          : franjas.length === 0
            ? <Vacio titulo="No queda nada libre ese día"
                     texto="Prueba con otra fecha." />
            : (
              <div className="cupos">
                {franjas.map(f => (
                  <button key={f.inicio} className="cupo"
                          onClick={() => setConfirmando(f)}>
                    <span className="cupo__hora">{soloHora(f.inicio)}</span>
                    <span className="cupo__fin">hasta {soloHora(f.fin)}</span>
                  </button>
                ))}
              </div>
            )}
      </Panel>

      {confirmando && (
        <Modal
          titulo="Confirmar la cita"
          onCerrar={() => setConfirmando(null)}
          pie={
            <>
              <button className="boton boton--suave" onClick={() => setConfirmando(null)}>
                Volver
              </button>
              <button className="boton boton--principal" onClick={reservar}
                      disabled={enviando}>
                {enviando ? 'Reservando…' : 'Reservar'}
              </button>
            </>
          }>
          <dl className="resumen">
            <div><dt>Servicio</dt><dd>{elegido.nombre}</dd></div>
            <div><dt>Atiende</dt><dd>{profesional?.nombre}</dd></div>
            <div><dt>Día</dt><dd>{fecha}</dd></div>
            <div>
              <dt>Hora</dt>
              <dd>{soloHora(confirmando.inicio)} a {soloHora(confirmando.fin)}</dd>
            </div>
            <div><dt>Precio</dt><dd>{pesos(elegido.precio)}</dd></div>
          </dl>
          <p className="nota">
            Si alguien toma esta hora mientras confirmas, te lo avisamos y podrás
            elegir otra.
          </p>
        </Modal>
      )}
    </>
  )
}

function enDias(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d
}
