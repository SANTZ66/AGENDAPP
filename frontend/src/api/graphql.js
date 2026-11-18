// Cliente GraphQL tolerante a fallos.
//
// Todo el frontend habla con el backend a traves de este archivo. No se usa
// ninguna libreria: GraphQL por debajo es un POST con un JSON que lleva la
// consulta y sus variables, y fetch alcanza de sobra.
//
// La direccion es relativa a proposito. En desarrollo la reenvia Vite y en
// produccion nginx, siempre al gateway. Asi el codigo nunca necesita saber
// donde vive el backend.
//
// ---------------------------------------------------------------------
// Por que este archivo no lanza excepciones
// ---------------------------------------------------------------------
// GraphQL responde 200 aunque algo haya fallado, y puede devolver las dos
// cosas a la vez: los datos que SI pudo resolver en "data", y la lista de
// lo que fallo en "errors". Si uno de los microservicios esta apagado, el
// gateway devuelve el resto de campos resueltos y solo ese en null.
//
// La version anterior lanzaba una excepcion en cuanto veia "errors", y con
// eso tiraba a la basura los datos buenos que venian en la misma respuesta.
// Resultado: apagar un servicio dejaba la pantalla entera en blanco con un
// mensaje tecnico encima.
//
// Ahora se devuelve siempre un objeto describiendo que llego y que no. Cada
// pantalla decide como seguir mostrandose con lo que tenga.

import { humanizar } from './mensajes.js'
import { reportar } from '../estado/conexion.js'
import { leer, vigente, cerrar, VENCIDA } from '../estado/sesion.js'

const RUTA = '/graphql'
const ESPERA_MAXIMA_MS = 8000

/**
 * Lanza una consulta de lectura. Nunca lanza una excepcion.
 *
 * @returns {{datos: object|null, caidos: string[], modo: 'ok'|'parcial'|'sin-servidor'}}
 *   datos   lo que el backend logro resolver, o null si no hubo respuesta
 *   caidos  nombres de los campos que fallaron, por ejemplo ['inventario']
 *   modo    'ok' todo bien · 'parcial' algo fallo · 'sin-servidor' no hubo respuesta
 */
export async function consultar(consulta, variables = {}) {
  const respuesta = await enviar(consulta, variables)

  if (!respuesta.hubo) {
    reportar(false)
    return { datos: null, caidos: [], modo: 'sin-servidor' }
  }

  reportar(true)

  const { cuerpo } = respuesta
  const errores = cuerpo.errors || []

  if (errores.length === 0) {
    return { datos: cuerpo.data || {}, caidos: [], modo: 'ok' }
  }

  // El campo "path" de cada error dice exactamente que parte de la consulta
  // fallo. Con eso sabemos que panel degradar y cuales dejar intactos.
  const caidos = [...new Set(
    errores.map(e => (Array.isArray(e.path) ? e.path[0] : null)).filter(Boolean)
  )]

  console.warn('[graphql] campos que fallaron:', caidos, errores)

  return { datos: cuerpo.data || {}, caidos, modo: 'parcial' }
}

/**
 * Ejecuta una mutacion. Tampoco lanza excepciones.
 *
 * A diferencia de una consulta, aqui el usuario pulso un boton y espera
 * saber si funciono, asi que siempre vuelve una frase lista para mostrar.
 *
 * @returns {{ok: boolean, datos: object|null, mensaje: string|null}}
 */
export async function ejecutar(consulta, variables = {}, respaldo) {
  const respuesta = await enviar(consulta, variables)

  if (!respuesta.hubo) {
    reportar(false)
    return {
      ok: false,
      datos: null,
      mensaje: 'No hay conexión con el servidor. Lo intentamos de nuevo en unos segundos.',
      codigo: null
    }
  }

  reportar(true)

  const { cuerpo } = respuesta
  const errores = cuerpo.errors || []

  if (errores.length > 0) {
    // "codigo" es la clasificacion que puso el gateway (UNAUTHORIZED,
    // FORBIDDEN...). Permite a una pantalla decidir por el tipo de error y no
    // por el texto, que humanizar() ya tradujo.
    const codigo = errores[0]?.extensions?.classification ?? null
    return { ok: false, datos: null, mensaje: humanizar(errores, respaldo), codigo }
  }

  return { ok: true, datos: cuerpo.data || {}, mensaje: null }
}

/** El token de la sesion, o null si no hay sesion o ya vencio (en cuyo caso se limpia). */
function tokenVigente() {
  const sesion = leer()
  if (!sesion) return null
  if (!vigente()) {
    cerrar(VENCIDA)
    return null
  }
  return sesion.token
}

/** true si algun error de la respuesta es UNAUTHORIZED. */
function esNoAutenticado(cuerpo) {
  return (cuerpo?.errors || []).some(e => e?.extensions?.classification === 'UNAUTHORIZED')
}

/**
 * La parte que toca la red. Devuelve { hubo: false } cuando no hubo
 * respuesta utilizable, sin distinguir el motivo: para quien usa la app da
 * igual si fue el DNS, un puerto cerrado o un 502.
 */
async function enviar(consulta, variables) {
  // Sin esto, una peticion a un servicio colgado deja la pantalla esperando
  // indefinidamente, que es peor que un error.
  const corte = new AbortController()
  const reloj = setTimeout(() => corte.abort(), ESPERA_MAXIMA_MS)

  // La cabecera solo viaja si hay una sesion vigente. Quien no inicio sesion
  // (toda la version cliente) envia exactamente la misma peticion de siempre.
  const token = tokenVigente()
  const cabeceras = { 'Content-Type': 'application/json' }
  if (token) cabeceras.Authorization = 'Bearer ' + token

  try {
    const respuesta = await fetch(RUTA, {
      method: 'POST',
      headers: cabeceras,
      body: JSON.stringify({ query: consulta, variables }),
      signal: corte.signal
    })

    if (!respuesta.ok) {
      console.warn('[graphql] el servidor respondio', respuesta.status)
      return { hubo: false }
    }

    const cuerpo = await respuesta.json()

    // El gateway rechazo el token que enviamos: la sesion ya no sirve. Se
    // cierra para que la pantalla vuelva al login en vez de seguir
    // reintentando. FORBIDDEN (sesion valida de otro establecimiento) NO la
    // cierra. Solo se cierra si la sesion guardada sigue siendo la que se
    // envio, por si mientras tanto se inicio otra.
    if (token && esNoAutenticado(cuerpo) && leer()?.token === token) {
      cerrar(VENCIDA)
    }

    return { hubo: true, cuerpo }
  } catch (e) {
    console.warn('[graphql] no hubo respuesta:', e.name === 'AbortError'
      ? 'se agoto la espera' : e.message)
    return { hubo: false }
  } finally {
    clearTimeout(reloj)
  }
}
