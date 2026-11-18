import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ejecutar } from '../../api/graphql.js'
import { useConsulta } from '../../ganchos/useConsulta.js'
import { useAvisos } from '../../ganchos/useAvisos.jsx'
import { SERVICIOS, DISPONIBILIDAD, RESERVAR } from '../../api/consultas.js'
import { soloHora, aFechaSimple, pesos } from '../../api/fechas.js'
import { leerCliente, guardarCliente } from '../../api/identidad.js'
import { avisarReserva } from '../../api/canal.js'
import { useEstablecimiento } from '../../contexto/Establecimiento.jsx'
import { Esmalte, Vacio } from '../../componentes/Piezas.jsx'

// Reservar como cliente, todo en una pagina.
//
// La idea es que no haya que decidir nada para llegar al final: al entrar ya
// esta elegido el primer establecimiento, su primera profesional, el primer
// servicio, mañana y la primera hora libre. La persona cambia solo lo que
// quiera y pulsa "Confirmar".
export default function ReservarCliente() {
  const {
    establecimientos, establecimientoId, setEstablecimientoId,
    profesionales, profesionalId, setProfesionalId, salon, profesional
  } = useEstablecimiento()
  const { mostrar } = useAvisos()

  const [cliente, setCliente] = useState(leerCliente)
  const [servicioId, setServicioId] = useState('')
  const [fecha, setFecha] = useState(() => aFechaSimple(enDias(1)))
  const [inicioElegido, setInicioElegido] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [hecha, setHecha] = useState(null)

  const equipo = profesionales.filter(p => p.activo !== false)

  const catalogo = useConsulta(
    SERVICIOS, { est: establecimientoId }, { activo: Boolean(establecimientoId) }
  )
  const servicios = catalogo.datos.servicios || []
  const servicio = servicios.find(s => s.id === servicioId) || servicios[0] || null

  const puedeBuscar = Boolean(profesionalId && servicio && fecha)
  const cupos = useConsulta(
    DISPONIBILIDAD,
    { prof: profesionalId, serv: servicio?.id || '', fecha },
    { activo: puedeBuscar, intervalo: 5000 }
  )
  const franjas = cupos.datos.disponibilidad || []
  const franja = franjas.find(f => f.inicio === inicioElegido) || franjas[0] || null

  const dias = Array.from({ length: 7 }, (_, i) => enDias(i))

  function cambiarEstablecimiento(id) {
    setEstablecimientoId(id)
    setServicioId('')
    setInicioElegido('')
  }

  async function reservar() {
    if (!cliente.nombre.trim()) {
      mostrar('Escribe tu nombre para poder reservar.', 'mal')
      return
    }
    if (!servicio || !franja || !profesionalId) return

    setEnviando(true)
    const yo = { id: cliente.id, nombre: cliente.nombre.trim() }
    guardarCliente(yo)

    const r = await ejecutar(
      RESERVAR,
      {
        est: establecimientoId, prof: profesionalId, serv: servicio.id,
        cli: yo.id, inicio: franja.inicio, fin: franja.fin
      },
      'No pudimos reservar esa hora. Inténtalo de nuevo.'
    )
    setEnviando(false)

    if (!r.ok) {
      mostrar(r.mensaje, 'mal')
      cupos.refrescar()
      return
    }

    avisarReserva({ establecimientoId, profesionalId })
    setHecha({
      establecimiento: salon?.nombre, profesional: profesional?.nombre,
      servicio: servicio.nombre, precio: servicio.precio,
      dia: nombreDelDia(fecha), inicio: franja.inicio, fin: franja.fin
    })
    setInicioElegido('')
    cupos.refrescar()
  }

  if (hecha) return <Listo datos={hecha} alOtra={() => setHecha(null)} />

  return (
    <>
      <header className="cliente__titular">
        <h1>Reserva tu cita</h1>
        <p>Ya dejamos elegido lo más común. Cambia lo que quieras y confirma.</p>
      </header>

      <div className="reserva">
        <div className="reserva__pasos">

          <Paso n={1} titulo="¿Dónde?">
            <div className="opciones">
              {establecimientos.map(e => (
                <button key={e.id} type="button"
                        className={'ficha' + (e.id === establecimientoId ? ' ficha--elegida' : '')}
                        aria-pressed={e.id === establecimientoId}
                        onClick={() => cambiarEstablecimiento(e.id)}>
                  <span className="ficha__inicial">{e.nombre.slice(0, 1).toUpperCase()}</span>
                  <span className="ficha__texto">
                    <span className="ficha__nombre">{e.nombre}</span>
                    {e.telefono && <span className="ficha__meta">{e.telefono}</span>}
                  </span>
                </button>
              ))}
            </div>
          </Paso>

          <Paso n={2} titulo="¿Con quién?">
            {equipo.length === 0
              ? <Vacio titulo="Este establecimiento aún no tiene equipo" />
              : (
                <div className="opciones">
                  {equipo.map(p => (
                    <button key={p.id} type="button"
                            className={'ficha' + (p.id === profesionalId ? ' ficha--elegida' : '')}
                            aria-pressed={p.id === profesionalId}
                            onClick={() => { setProfesionalId(p.id); setInicioElegido('') }}>
                      <span className="ficha__inicial ficha__inicial--persona">
                        {p.nombre.slice(0, 1).toUpperCase()}
                      </span>
                      <span className="ficha__texto">
                        <span className="ficha__nombre">{p.nombre}</span>
                      </span>
                    </button>
                  ))}
                </div>
              )}
          </Paso>

          <Paso n={3} titulo="¿Qué servicio?">
            {servicios.length === 0
              ? <Vacio titulo="No hay servicios disponibles" texto="Prueba con otro establecimiento." />
              : (
                <div className="servicios">
                  {servicios.map(s => (
                    <button key={s.id} type="button"
                            className={'servicio' + (servicio?.id === s.id ? ' servicio--elegido' : '')}
                            aria-pressed={servicio?.id === s.id}
                            onClick={() => { setServicioId(s.id); setInicioElegido('') }}>
                      <Esmalte servicio={s.nombre} tam={12} />
                      <span className="servicio__nombre">{s.nombre}</span>
                      <span className="servicio__meta">{s.duracionMinutos} min · {pesos(s.precio)}</span>
                    </button>
                  ))}
                </div>
              )}
          </Paso>

          <Paso n={4} titulo="¿Qué día y a qué hora?">
            <div className="dias">
              {dias.map((d, i) => {
                const valor = aFechaSimple(d)
                return (
                  <button key={valor} type="button"
                          className={'dia' + (valor === fecha ? ' dia--elegido' : '')}
                          aria-pressed={valor === fecha}
                          onClick={() => { setFecha(valor); setInicioElegido('') }}>
                    <span className="dia__nombre">
                      {i === 0 ? 'Hoy' : i === 1 ? 'Mañana'
                        : d.toLocaleDateString('es-CO', { weekday: 'short' }).replace('.', '')}
                    </span>
                    <span className="dia__numero">{d.getDate()}</span>
                  </button>
                )
              })}
            </div>

            {!puedeBuscar
              ? <p className="nota">Elige una profesional y un servicio para ver las horas.</p>
              : cupos.cargandoPrimera
                ? <p className="nota">Buscando horas libres…</p>
                : franjas.length === 0
                  ? <Vacio titulo="No queda nada libre ese día" texto="Prueba con otro día." />
                  : (
                    <div className="cupos">
                      {franjas.map(f => (
                        <button key={f.inicio} type="button"
                                className={'cupo' + (franja?.inicio === f.inicio ? ' cupo--elegido' : '')}
                                aria-pressed={franja?.inicio === f.inicio}
                                onClick={() => setInicioElegido(f.inicio)}>
                          <span className="cupo__hora">{soloHora(f.inicio)}</span>
                          <span className="cupo__fin">hasta {soloHora(f.fin)}</span>
                        </button>
                      ))}
                    </div>
                  )}
          </Paso>

          <Paso n={5} titulo="¿A nombre de quién?">
            <label className="campo campo--medio">
              <span>Tu nombre</span>
              <input type="text" value={cliente.nombre} maxLength={60}
                     onChange={e => setCliente({ ...cliente, nombre: e.target.value })} />
            </label>
          </Paso>
        </div>

        <aside className="resumen-cita">
          <h2>Tu cita</h2>
          <dl className="resumen">
            <div><dt>Lugar</dt><dd>{salon?.nombre || '—'}</dd></div>
            <div><dt>Atiende</dt><dd>{profesional?.nombre || '—'}</dd></div>
            <div><dt>Servicio</dt><dd>{servicio?.nombre || '—'}</dd></div>
            <div><dt>Día</dt><dd>{nombreDelDia(fecha)}</dd></div>
            <div>
              <dt>Hora</dt>
              <dd>{franja ? `${soloHora(franja.inicio)} a ${soloHora(franja.fin)}` : '—'}</dd>
            </div>
            <div><dt>Precio</dt><dd>{servicio ? pesos(servicio.precio) : '—'}</dd></div>
          </dl>
          <button className="boton boton--principal boton--ancho"
                  onClick={reservar}
                  disabled={enviando || !franja || !servicio || !profesionalId}>
            {enviando ? 'Reservando…' : 'Confirmar cita'}
          </button>
          <p className="nota">Si alguien toma esa hora antes que tú, te avisamos y eliges otra.</p>
        </aside>
      </div>
    </>
  )
}

function Paso({ n, titulo, children }) {
  return (
    <section className="paso">
      <h2 className="paso__titulo"><span className="paso__numero">{n}</span>{titulo}</h2>
      {children}
    </section>
  )
}

function Listo({ datos, alOtra }) {
  return (
    <div className="listo">
      <span className="listo__marca" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor"
             strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
          <path d="M5 12.5 10 17.5 19 7" />
        </svg>
      </span>
      <h1>¡Tu cita quedó reservada!</h1>
      <p className="listo__texto">
        {datos.establecimiento} ya la ve en su agenda.
      </p>
      <dl className="resumen resumen--tarjeta">
        <div><dt>Lugar</dt><dd>{datos.establecimiento}</dd></div>
        <div><dt>Atiende</dt><dd>{datos.profesional}</dd></div>
        <div><dt>Servicio</dt><dd>{datos.servicio}</dd></div>
        <div><dt>Día</dt><dd>{datos.dia}</dd></div>
        <div><dt>Hora</dt><dd>{soloHora(datos.inicio)} a {soloHora(datos.fin)}</dd></div>
        <div><dt>Precio</dt><dd>{pesos(datos.precio)}</dd></div>
      </dl>
      <div className="listo__acciones">
        <Link to="/cliente/mis-citas" className="boton boton--principal">Ver mis citas</Link>
        <button className="boton boton--suave" onClick={alOtra}>Reservar otra</button>
      </div>
    </div>
  )
}

function enDias(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d
}

function nombreDelDia(fechaSimple) {
  const [a, m, d] = fechaSimple.split('-').map(Number)
  return new Date(a, m - 1, d).toLocaleDateString('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long'
  })
}
