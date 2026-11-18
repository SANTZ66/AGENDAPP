import { Link } from 'react-router-dom'
import { useConsulta } from '../ganchos/useConsulta.js'
import { HOY } from '../api/consultas.js'
import { rangoDelDia, diaLargo, soloHora, pesos, cantidad, etiquetaCliente } from '../api/fechas.js'
import { useEnVivo } from '../ganchos/useEnVivo.jsx'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'
import Panel, { SinInformacion } from '../componentes/Panel.jsx'
import { Estado, EtiquetaConfirmada, Esmalte, Cifra, Vacio, colorDeServicio } from '../componentes/Piezas.jsx'
import { IconoAlerta, IconoFlecha } from '../componentes/Iconos.jsx'

const REFRESCO_MS = 4000
const JORNADA = { abre: 8, cierra: 19 }

// La pantalla de inicio: el dia de hoy de un vistazo.
//
// Es una sola consulta al gateway que por dentro toca tres microservicios.
// Si alguno no responde, los demas bloques siguen en pie: esa es toda la
// gracia de resolverlo por campos y no por pantalla.
export default function Hoy() {
  const { establecimientoId, profesionalId, profesional } = useEstablecimiento()
  const { desde, hasta } = rangoDelDia()
  const { nuevas } = useEnVivo()

  const listo = Boolean(establecimientoId && profesionalId)
  const { datos, desactualizados, vacios, cargandoPrimera, actualizadoEn, refrescar } =
    useConsulta(
      HOY,
      { prof: profesionalId, est: establecimientoId, desde, hasta },
      { activo: listo, intervalo: REFRESCO_MS }
    )

  const citas = [...(datos.agenda || [])].sort((a, b) => a.inicio.localeCompare(b.inicio))
  const resumen = datos.resumenIngresos
  const inventario = datos.inventario || []
  const porReponer = inventario.filter(i => i.enUmbral)

  const pendientes = citas.filter(c => c.estado === 'agendada')
  const atendidas = citas.filter(c => c.estado === 'completada')

  const sinAgenda = vacios.includes('agenda') || vacios.includes('todo')
  const sinNumeros = vacios.includes('resumenIngresos') || vacios.includes('todo')
  const sinStock = vacios.includes('inventario') || vacios.includes('todo')

  return (
    <>
      <header className="titular">
        <div>
          <p className="titular__fecha">{diaLargo()}</p>
          <h1 className="titular__texto">
            {saludo()}{profesional ? ', ' + primerNombre(profesional.nombre) : ''}
          </h1>
        </div>
        <p className="envivo">
          <span className="envivo__punto" aria-hidden="true" />
          En vivo · las reservas aparecen solas
        </p>
      </header>

      <div className="cifras">
        <Cifra rotulo="Citas de hoy" valor={cargandoPrimera ? '—' : citas.length}
               pie={pendientes.length + ' por atender'} />
        <Cifra rotulo="Atendidas" valor={cargandoPrimera ? '—' : atendidas.length}
               pie="cerradas hoy" />
        {sinNumeros ? (
          <Cifra rotulo="Ingresos de hoy" valor="—" pie="no disponible ahora" tono="apagado" />
        ) : (
          <Cifra rotulo="Ingresos de hoy" valor={pesos(resumen?.totalIngresos)}
                 pie={(resumen?.serviciosPrestados ?? 0) + ' servicios liquidados'} />
        )}
        {sinStock ? (
          <Cifra rotulo="Por reponer" valor="—" pie="no disponible ahora" tono="apagado" />
        ) : (
          <Cifra rotulo="Por reponer" valor={porReponer.length}
                 pie={porReponer.length === 0 ? 'todo en orden' : 'insumos bajo el mínimo'}
                 tono={porReponer.length > 0 ? 'aviso' : 'normal'} />
        )}
      </div>

      <div className="rejilla-hoy">
        <Panel
          titulo="La jornada"
          apunte="Las horas del día con lo que hay reservado en cada una."
          cargando={cargandoPrimera && listo}
          sinInformacion={sinAgenda}
          queEs="La agenda"
          desactualizado={desactualizados.includes('agenda')}
          actualizadoEn={actualizadoEn}
          alReintentar={refrescar}
          compacto>
          {!listo
            ? <Vacio titulo="Elige quién atiende"
                     texto="Selecciona a alguien del equipo en la barra lateral." />
            : <Jornada citas={citas} nuevas={nuevas} />}
        </Panel>

        <div className="columna">
          <Panel
            titulo="Por reponer"
            cargando={cargandoPrimera && listo}
            sinInformacion={sinStock}
            queEs="El inventario"
            desactualizado={desactualizados.includes('inventario')}
            actualizadoEn={actualizadoEn}
            alReintentar={refrescar}
            compacto>
            {porReponer.length === 0
              ? <Vacio titulo="Nada por reponer"
                       texto="Todos los insumos están por encima de su mínimo." />
              : (
                <ul className="lista-reponer">
                  {porReponer.map(i => (
                    <li key={i.id}>
                      <span className="lista-reponer__aviso" aria-hidden="true">
                        <IconoAlerta tam={15} />
                      </span>
                      <span className="lista-reponer__nombre">{i.nombre}</span>
                      <span className="lista-reponer__cifra">
                        quedan {cantidad(i.existencia, i.unidad)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
          </Panel>

          <Panel titulo="Siguiente" compacto>
            {pendientes.length === 0
              ? <Vacio titulo="No queda nada pendiente"
                       texto="Ya atendiste todas las citas de hoy." />
              : <Siguiente cita={pendientes[0]} />}
          </Panel>
        </div>
      </div>
    </>
  )
}

/**
 * La jornada dibujada hora por hora.
 *
 * Mostrar tambien las horas libres, y no solo las ocupadas, es lo que hace
 * que esto sirva para trabajar: de un vistazo se ve donde cabe alguien mas.
 */
function Jornada({ citas, nuevas }) {
  const horas = []
  for (let h = JORNADA.abre; h <= JORNADA.cierra; h++) horas.push(h)

  const porHora = new Map()
  citas.forEach(c => {
    const h = new Date(c.inicio).getHours()
    if (!porHora.has(h)) porHora.set(h, [])
    porHora.get(h).push(c)
  })

  const ahora = new Date().getHours()

  return (
    <ol className="jornada">
      {horas.map(h => {
        const deEstaHora = porHora.get(h) || []
        const esAhora = h === ahora
        return (
          <li key={h} className={'jornada__fila' + (esAhora ? ' jornada__fila--ahora' : '')}>
            <span className="jornada__hora">{String(h).padStart(2, '0')}:00</span>
            <div className="jornada__ranura">
              {deEstaHora.length === 0
                ? <span className="jornada__libre">Libre</span>
                : deEstaHora.map(c => (
                    <article key={c.id}
                             className={'cita' + (nuevas.has(c.id) ? ' cita--nueva' : '')}
                             style={{ '--color': colorDeServicio(c.servicio) }}>
                      <div className="cita__linea" aria-hidden="true" />
                      <div className="cita__texto">
                        <p className="cita__servicio">{c.servicio}</p>
                        <p className="cita__detalle">
                          {c.duracionMinutos} min · {etiquetaCliente(c.clienteId)}
                        </p>
                      </div>
                      {nuevas.has(c.id) && <span className="etiqueta-nueva">Nueva</span>}
                      {c.estado === 'agendada' && c.confirmadaEn && <EtiquetaConfirmada />}
                      <Estado valor={c.estado} />
                    </article>
                  ))}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

function Siguiente({ cita }) {
  return (
    <Link to="/negocio/agenda" className="siguiente"
          style={{ '--color': colorDeServicio(cita.servicio) }}>
      <p className="siguiente__hora">{soloHora(cita.inicio)}</p>
      <p className="siguiente__servicio">
        <Esmalte servicio={cita.servicio} />
        {cita.servicio}
      </p>
      <p className="siguiente__cliente">{etiquetaCliente(cita.clienteId)}</p>
      <span className="siguiente__ir">
        Ir a la agenda <IconoFlecha tam={14} />
      </span>
    </Link>
  )
}

function saludo() {
  const h = new Date().getHours()
  if (h < 12) return 'Buenos días'
  if (h < 19) return 'Buenas tardes'
  return 'Buenas noches'
}

function primerNombre(nombre = '') {
  return nombre.trim().split(' ')[0]
}
