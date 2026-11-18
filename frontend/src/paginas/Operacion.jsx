import { useConsulta } from '../ganchos/useConsulta.js'
import { OPERACION } from '../api/consultas.js'
import { rangoDelAnio, pesos, cantidad } from '../api/fechas.js'
import { useEstablecimiento } from '../contexto/Establecimiento.jsx'
import Panel from '../componentes/Panel.jsx'
import { Cifra, Vacio } from '../componentes/Piezas.jsx'

const REFRESCO_MS = 5000

// El resultado de los eventos: ingresos liquidados e inventario descontado.
//
// Nada de lo que se ve aqui lo escribio una persona. Todo lo produjo
// ms-operaciones al consumir eventos de citas completadas. Con el simulador
// encendido, los numeros se mueven solos en pantalla.
//
// Esta pantalla es ademas la mejor para enseñar la tolerancia a fallos: los
// dos bloques salen de una misma consulta pero de campos distintos, asi que
// pueden caer por separado.
export default function Operacion() {
  const { establecimientoId } = useEstablecimiento()
  const { desde, hasta } = rangoDelAnio()

  const { datos, desactualizados, vacios, cargandoPrimera, actualizadoEn, refrescar } =
    useConsulta(
      OPERACION,
      { est: establecimientoId, desde, hasta },
      { activo: Boolean(establecimientoId), intervalo: REFRESCO_MS }
    )

  const resumen = datos.resumenIngresos
  const inventario = datos.inventario || []
  const porReponer = inventario.filter(i => i.enUmbral)

  const sinNumeros = vacios.includes('resumenIngresos') || vacios.includes('todo')
  const sinStock = vacios.includes('inventario') || vacios.includes('todo')

  return (
    <>
      <header className="titular">
        <div>
          <p className="titular__fecha">Operación</p>
          <h1 className="titular__texto">Ingresos y materiales</h1>
        </div>
      </header>

      <p className="intro">
        Estos números no los escribe nadie: se arman solos cada vez que se cierra
        una cita.
      </p>

      <div className="cifras">
        {sinNumeros ? (
          <>
            <Cifra rotulo="Servicios prestados" valor="—" pie="no disponible ahora" tono="apagado" />
            <Cifra rotulo="Ingresos del año" valor="—" pie="no disponible ahora" tono="apagado" />
          </>
        ) : (
          <>
            <Cifra rotulo="Servicios prestados" valor={resumen?.serviciosPrestados ?? 0}
                   pie="liquidados en el año" />
            <Cifra rotulo="Ingresos del año" valor={pesos(resumen?.totalIngresos)}
                   pie="suma de los cobros" />
          </>
        )}
        {sinStock ? (
          <>
            <Cifra rotulo="Por reponer" valor="—" pie="no disponible ahora" tono="apagado" />
            <Cifra rotulo="Materiales" valor="—" pie="no disponible ahora" tono="apagado" />
          </>
        ) : (
          <>
            <Cifra rotulo="Por reponer" valor={porReponer.length}
                   pie={porReponer.length === 0 ? 'todo en orden' : 'bajo el mínimo'}
                   tono={porReponer.length > 0 ? 'aviso' : 'normal'} />
            <Cifra rotulo="Materiales" valor={inventario.length} pie="en el inventario" />
          </>
        )}
      </div>

      <Panel
        titulo="Inventario"
        apunte="Se descuenta solo al cerrar cada cita, según lo que gasta el servicio."
        cargando={cargandoPrimera}
        sinInformacion={sinStock}
        queEs="El inventario"
        desactualizado={desactualizados.includes('inventario')}
        actualizadoEn={actualizadoEn}
        alReintentar={refrescar}
        compacto>
        {inventario.length === 0
          ? <Vacio titulo="No hay materiales registrados"
                   texto="Agrega insumos para llevar su control." />
          : (
            <table className="tabla">
              <thead>
                <tr>
                  <th>Material</th>
                  <th className="num">Quedan</th>
                  <th className="num">Mínimo</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {inventario.map(i => (
                  <tr key={i.id} className={i.enUmbral ? 'fila--aviso' : ''}>
                    <td>{i.nombre}</td>
                    <td className="num">{cantidad(i.existencia, i.unidad)}</td>
                    <td className="num tenue">{cantidad(i.umbralMinimo)}</td>
                    <td>
                      <span className={'estado estado--' + (i.enUmbral ? 'anulado' : 'hecho')}>
                        <span className="estado__punto" />
                        {i.enUmbral ? 'Reponer' : 'Suficiente'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </Panel>
    </>
  )
}
