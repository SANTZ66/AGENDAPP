// Un bloque de contenido con sus tres estados posibles.
//
// Aqui vive la regla de oro de esta version: cuando una parte del sistema
// no responde, la pantalla no muestra un error, muestra lo que sabe.
//
//   cargando        -> lineas grises con pulso, para que no salte el diseño
//   sin informacion -> una nota tranquila y un boton para volver a intentar
//   desactualizado  -> el contenido de siempre, con la hora de la ultima
//                      lectura buena en letra pequeña
//
// Ninguno de los tres habla de servicios, ni de codigos, ni de Kafka.

import { IconoNube, IconoReintentar } from './Iconos.jsx'

export default function Panel({
  titulo,
  apunte,
  acciones,
  cargando = false,
  sinInformacion = false,
  desactualizado = false,
  actualizadoEn = null,
  queEs = 'Esta información',
  alReintentar,
  compacto = false,
  children
}) {
  return (
    <section className="panel">
      {(titulo || acciones) && (
        <header className="panel__cabecera">
          <div>
            {titulo && <h2 className="panel__titulo">{titulo}</h2>}
            {apunte && <p className="panel__apunte">{apunte}</p>}
          </div>
          <div className="panel__acciones">
            {desactualizado && <SelloDesactualizado desde={actualizadoEn} />}
            {acciones}
          </div>
        </header>
      )}

      <div className={compacto ? 'panel__cuerpo panel__cuerpo--compacto' : 'panel__cuerpo'}>
        {cargando
          ? <Esqueleto />
          : sinInformacion
            ? <SinInformacion queEs={queEs} alReintentar={alReintentar} />
            : children}
      </div>
    </section>
  )
}

/**
 * Lo que ve el usuario cuando un servicio lleva caido desde antes de que
 * hubiera nada que mostrar. No es un error: es una ausencia, contada como
 * tal, con la unica accion que tiene sentido ofrecer.
 */
export function SinInformacion({ queEs = 'Esta información', alReintentar }) {
  return (
    <div className="sin-info">
      <span className="sin-info__icono"><IconoNube tam={26} /></span>
      <p className="sin-info__titulo">{queEs} no está disponible ahora</p>
      <p className="sin-info__texto">
        Lo estamos reintentando. Mientras tanto puedes seguir con el resto.
      </p>
      {alReintentar && (
        <button className="boton boton--suave" onClick={alReintentar}>
          <IconoReintentar tam={15} />
          Reintentar ahora
        </button>
      )}
    </div>
  )
}

/**
 * Pantalla completa para cuando no hay nada en pie.
 *
 * Es distinta de SinInformacion a proposito: ahi falta una parte y el resto
 * sirve, aqui no sirve nada, asi que decirle a la persona que "siga usando
 * el resto" seria mentirle. Se reintenta sola y se dice con esas palabras,
 * para que nadie se quede mirando la pantalla sin saber si hacer algo.
 */
export function SinConexion({ alReintentar }) {
  return (
    <div className="desconectado">
      <span className="desconectado__gota" aria-hidden="true" />
      <h1>No encontramos el servidor</h1>
      <p>
        La app está funcionando, pero no logra conectarse para traer los datos
        del salón. Seguimos intentándolo cada pocos segundos.
      </p>
      {alReintentar && (
        <button className="boton boton--principal" onClick={alReintentar}>
          <IconoReintentar tam={15} />
          Intentar de nuevo
        </button>
      )}
      <p className="desconectado__pista">
        Si acabas de encender el sistema, dale un momento: los servicios tardan
        en levantarse del todo.
      </p>
    </div>
  )
}

/** Marca discreta de que lo que se ve es la ultima lectura buena. */
export function SelloDesactualizado({ desde }) {
  return (
    <span className="sello-viejo" title="Mostrando la última lectura disponible">
      <span className="sello-viejo__punto" />
      {desde ? 'Última lectura ' + haceCuanto(desde) : 'Sin actualizar'}
    </span>
  )
}

/** Lineas grises mientras llega la primera respuesta. */
export function Esqueleto({ lineas = 4 }) {
  return (
    <div className="esqueleto">
      {Array.from({ length: lineas }).map((_, i) => (
        <div key={i} className="esqueleto__linea" style={{ width: anchos[i % anchos.length] }} />
      ))}
    </div>
  )
}

const anchos = ['92%', '74%', '83%', '61%']

function haceCuanto(fecha) {
  const segundos = Math.max(0, Math.round((Date.now() - fecha.getTime()) / 1000))
  if (segundos < 60) return 'hace un momento'
  const minutos = Math.round(segundos / 60)
  if (minutos < 60) return `hace ${minutos} min`
  const horas = Math.round(minutos / 60)
  return `hace ${horas} h`
}
