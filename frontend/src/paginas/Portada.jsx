import { Link } from 'react-router-dom'
import { IconoClientas, IconoOperacion } from '../componentes/Iconos.jsx'

// La puerta de entrada. Hay dos versiones de la misma plataforma:
//
//   Cliente         quien quiere una cita: elige lugar, persona y hora.
//   Establecimiento quien presta el servicio: ve llegar las reservas, cierra
//                   citas y controla inventario e ingresos.
//
// Ambas hablan con el mismo backend, asi que lo que hace una se ve en la otra.
export default function Portada() {
  return (
    <div className="portada">
      <header className="portada__cabecera">
        <span className="lateral__gota" aria-hidden="true" />
        <h1>AGENDAPP</h1>
        <p>Elige desde qué lado quieres entrar.</p>
      </header>

      <div className="portada__opciones">
        <Opcion
          a="/cliente"
          tono="cliente"
          Icono={IconoClientas}
          rotulo="Versión cliente"
          titulo="Quiero reservar una cita"
          texto="Elige el establecimiento, la profesional y la hora. Ya te dejamos todo preseleccionado."
          accion="Entrar como cliente" />

        <Opcion
          a="/negocio"
          tono="negocio"
          Icono={IconoOperacion}
          rotulo="Versión establecimiento"
          titulo="Administro un establecimiento"
          texto="Ve llegar las reservas en vivo, atiende y cierra citas, revisa inventario e ingresos."
          accion="Entrar al panel" />
      </div>

      <p className="portada__pie">
        Para presentarlo: abre cada versión en su propia pestaña y reserva en una mientras
        miras la otra.
      </p>
    </div>
  )
}

function Opcion({ a, tono, Icono, rotulo, titulo, texto, accion }) {
  return (
    <article className={'opcion opcion--' + tono}>
      <span className="opcion__icono"><Icono tam={26} /></span>
      <p className="opcion__rotulo">{rotulo}</p>
      <h2 className="opcion__titulo">{titulo}</h2>
      <p className="opcion__texto">{texto}</p>
      <div className="opcion__acciones">
        <Link to={a} className="boton boton--principal">{accion}</Link>
        <a href={a} target="_blank" rel="noreferrer" className="boton boton--suave">
          Abrir en pestaña nueva
        </a>
      </div>
    </article>
  )
}
