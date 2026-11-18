// La sesion del panel del establecimiento.
//
// Guarda lo que devolvio el login: { token, expiraEn, nombre, establecimientoId }.
// Vive fuera de React, igual que estado/conexion.js, porque la escribe y la
// consulta el cliente GraphQL, que no es un componente.
//
// Se guarda en localStorage y no en sessionStorage a proposito: la portada
// ofrece "Abrir en pestana nueva", y con sessionStorage cada pestana
// obligaria a iniciar sesion otra vez. Como localStorage se comparte, cerrar
// sesion en una pestana avisa a las demas mediante el evento "storage".
//
// Esto solo afecta a la version del establecimiento. La version cliente no
// inicia sesion y nunca escribe aqui.
//
// Una sesion puede terminar de dos formas, y a la persona no se le dice lo
// mismo en cada una:
//   - voluntaria: pulso "Cerrar sesion". No hace falta avisarle de nada.
//   - vencida:    el token expiro o el gateway lo rechazo. Hay que explicarle
//                 por que la pantalla volvio al login.

const CLAVE = 'citas.sesion.negocio'

export const VOLUNTARIO = 'voluntario'
export const VENCIDA = 'vencida'

const oyentes = new Set()
let enMemoria = null     // respaldo si el navegador no deja usar localStorage
let temporizador = null
let ultima = null        // la ultima sesion que se vio, para saber como termino
let motivoCierre = null

function limpia(valor) {
  if (!valor || typeof valor.token !== 'string' || !valor.token) return null
  const { token, expiraEn, nombre, establecimientoId } = valor
  return { token, expiraEn, nombre, establecimientoId }
}

/** La sesion guardada, venza o no. Null si no hay ninguna. */
export function leer() {
  try {
    const crudo = localStorage.getItem(CLAVE)
    return limpia(crudo ? JSON.parse(crudo) : null)
  } catch {
    return limpia(enMemoria)
  }
}

/** true si hay sesion guardada y todavia no ha vencido. */
export function vigente() {
  const sesion = leer()
  if (!sesion) return false
  const vence = Date.parse(sesion.expiraEn)
  return Number.isFinite(vence) && vence > Date.now()
}

/** Guarda la sesion que devolvio el login y avisa a quien este suscrito. */
export function iniciar({ token, expiraEn, nombre, establecimientoId }) {
  enMemoria = { token, expiraEn, nombre, establecimientoId }
  try { localStorage.setItem(CLAVE, JSON.stringify(enMemoria)) } catch { /* sin almacenamiento */ }
  ultima = enMemoria
  motivoCierre = null
  programarVencimiento()
  avisar()
}

/**
 * Borra la sesion y avisa. Es seguro llamarla aunque no haya ninguna.
 *
 * Sin argumentos es un cierre voluntario. El cliente GraphQL y el
 * temporizador pasan VENCIDA cuando la sesion deja de servir por su cuenta.
 */
export function cerrar(motivo = VOLUNTARIO) {
  enMemoria = null
  try { localStorage.removeItem(CLAVE) } catch { /* sin almacenamiento */ }
  if (ultima) motivoCierre = motivo
  ultima = null
  programarVencimiento()
  avisar()
}

/**
 * Como termino la ultima sesion (VOLUNTARIO, VENCIDA o null si no hubo
 * ninguna) y la olvida, para que cada cierre se explique una sola vez.
 */
export function consumirMotivoDeCierre() {
  const motivo = motivoCierre
  motivoCierre = null
  return motivo
}

/** Suscribe a los cambios de sesion. Devuelve la funcion para darse de baja. */
export function alCambiar(oyente) {
  oyentes.add(oyente)
  return () => oyentes.delete(oyente)
}

function avisar() {
  oyentes.forEach(oyente => oyente())
}

// Cuando la sesion vence se cierra sola, sin esperar a que una peticion falle.
// Una peticion con token vencido ni siquiera se envia con cabecera, asi que
// sin este aviso la pantalla se quedaria abierta mostrando paneles vacios.
// Si ya vencio (por ejemplo, se abre la aplicacion al dia siguiente), se
// cierra en el acto.
function programarVencimiento() {
  if (temporizador) {
    clearTimeout(temporizador)
    temporizador = null
  }
  const sesion = leer()
  if (!sesion) return
  const falta = Date.parse(sesion.expiraEn) - Date.now()
  if (!Number.isFinite(falta)) return
  if (falta <= 0) {
    cerrar(VENCIDA)
    return
  }
  // setTimeout no admite mas de ~24 dias; la sesion dura horas, pero se acota.
  temporizador = setTimeout(() => cerrar(VENCIDA), Math.min(falta, 2147483647))
}

if (typeof window !== 'undefined') {
  // El evento solo llega a las DEMAS pestanas: es lo que sincroniza el cierre
  // de sesion (o el inicio) entre ellas. key es null cuando se vacia el almacen.
  window.addEventListener('storage', e => {
    if (e.key !== CLAVE && e.key !== null) return
    const ahora = leer()
    if (ultima && !ahora) {
      // La sesion desaparecio por obra de otra pestana. Si ya habia vencido
      // fue por vencimiento; si no, alguien cerro sesion a proposito.
      motivoCierre = Date.parse(ultima.expiraEn) <= Date.now() ? VENCIDA : VOLUNTARIO
    } else if (ahora) {
      motivoCierre = null
    }
    ultima = ahora
    programarVencimiento()
    avisar()
  })
  ultima = leer()
  programarVencimiento()
}
