import { useConsulta } from '../ganchos/useConsulta.js'
import { CLIENTES_INACTIVOS, NOTIFICACIONES } from '../api/consultas.js'
import { legible, soloDia, desdeHace, etiquetaCliente } from '../api/fechas.js'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'
import Panel from '../componentes/Panel.jsx'
import { Vacio } from '../componentes/Piezas.jsx'

const TIPOS = {
  'confirmacion-reserva': { texto: 'Reserva confirmada', tono: 'hecho' },
  'solicitud-valoracion': { texto: 'Valoración pedida', tono: 'espera' },
  'alerta-insumo': { texto: 'Material bajo', tono: 'anulado' },
  'recordatorio-cita': { texto: 'Recordatorio', tono: 'espera' },
  'aviso-cancelacion': { texto: 'Cancelación', tono: 'anulado' }
}

// El seguimiento a las clientas.
//
// Las dos listas salen de ms-relacion-cliente, que no sabe nada de citas:
// todo lo que muestra lo construyo escuchando eventos. Es el servicio que
// resuelve el problema P6, el de no tener forma de saber quien no ha vuelto.

// Cada cuanto se vuelven a pedir los avisos y las clientas inactivas.
const REFRESCO_MS = 4000

export default function Clientas() {
  const { establecimientoId } = useEstablecimiento()
  const activo = Boolean(establecimientoId)

  const inactivas = useConsulta(CLIENTES_INACTIVOS, { est: establecimientoId }, { activo, intervalo: REFRESCO_MS })
  const avisos = useConsulta(NOTIFICACIONES, { est: establecimientoId }, { activo, intervalo: REFRESCO_MS })

  const lista = inactivas.datos.clientesInactivos || []
  const notificaciones = avisos.datos.notificaciones || []

  return (
    <>
      <header className="titular">
        <div>
          <p className="titular__fecha">Clientas</p>
          <h1 className="titular__texto">Quién no ha vuelto</h1>
        </div>
      </header>

      <Panel
        titulo="Toca llamarlas"
        apunte="Pasó el tiempo que suele durar su servicio y no han vuelto a reservar."
        cargando={inactivas.cargandoPrimera && activo}
        sinInformacion={inactivas.vacios.length > 0}
        queEs="La lista de clientas"
        desactualizado={inactivas.desactualizados.length > 0}
        actualizadoEn={inactivas.actualizadoEn}
        alReintentar={inactivas.refrescar}
        compacto>
        {lista.length === 0
          ? <Vacio titulo="Todas al día"
                   texto="Nadie se ha pasado de su fecha de vuelta." />
          : (
            <ul className="clientas">
              {lista.map(c => (
                <li key={c.clienteId} className="clientas__fila">
                  <span className="clientas__inicial" aria-hidden="true">
                    {etiquetaCliente(c.clienteId).slice(0, 1).toUpperCase()}
                  </span>
                  <div className="clientas__quien">
                    <p className="clientas__nombre">{etiquetaCliente(c.clienteId)}</p>
                    <p className="clientas__detalle">
                      {c.totalVisitas} visita{c.totalVisitas === 1 ? '' : 's'}
                      {c.totalCancelaciones > 0 && ` · ${c.totalCancelaciones} anulada${c.totalCancelaciones === 1 ? '' : 's'}`}
                    </p>
                  </div>
                  <div className="clientas__cuando">
                    <p className="clientas__ultima">Última visita {desdeHace(c.ultimaVisita)}</p>
                    <p className="clientas__esperada">
                      Se esperaba el {soloDia(c.proximoContacto)}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
      </Panel>

      <Panel
        titulo="Avisos enviados"
        apunte="Se generan solos cuando pasa algo que la clienta debe saber."
        cargando={avisos.cargandoPrimera && activo}
        sinInformacion={avisos.vacios.length > 0}
        queEs="Los avisos"
        desactualizado={avisos.desactualizados.length > 0}
        actualizadoEn={avisos.actualizadoEn}
        alReintentar={avisos.refrescar}
        compacto>
        {notificaciones.length === 0
          ? <Vacio titulo="Todavía no hay avisos"
                   texto="Aparecerán al cerrar o anular una cita." />
          : (
            <ul className="avisos-lista">
              {notificaciones.map(n => {
                const t = TIPOS[n.tipo] || { texto: n.tipo, tono: 'espera' }
                return (
                  <li key={n.id} className="avisos-lista__fila">
                    <div className="avisos-lista__cab">
                      <span className={'estado estado--' + t.tono}>
                        <span className="estado__punto" />
                        {t.texto}
                      </span>
                      <span className="avisos-lista__cuando">{legible(n.creadaEn)}</span>
                    </div>
                    <p className="avisos-lista__asunto">{n.asunto}</p>
                    <p className="avisos-lista__cuerpo">{n.cuerpo}</p>
                  </li>
                )
              })}
            </ul>
          )}
      </Panel>
    </>
  )
}
