import { nombreDeCliente } from './identidad.js'

// Utilidades de fecha.
//
// Todo el sistema trabaja en la zona horaria de Colombia (UTC-5) y espera
// fechas ISO-8601 con ese desfase.

export const DESFASE = '-05:00'

/** Rango que cubre el año en curso. Para las consultas de periodo. */
export function rangoDelAnio() {
  const anio = new Date().getFullYear()
  return {
    desde: `${anio}-01-01T00:00:00${DESFASE}`,
    hasta: `${anio}-12-31T23:59:59${DESFASE}`
  }
}

/** Rango de un dia concreto, de medianoche a medianoche. */
export function rangoDelDia(fecha = new Date()) {
  const dia = aFechaSimple(fecha)
  return {
    desde: `${dia}T00:00:00${DESFASE}`,
    hasta: `${dia}T23:59:59${DESFASE}`
  }
}

/** Convierte un Date a "2026-11-12", en hora local y no en UTC. */
export function aFechaSimple(fecha) {
  const p = n => String(n).padStart(2, '0')
  return `${fecha.getFullYear()}-${p(fecha.getMonth() + 1)}-${p(fecha.getDate())}`
}

/** "jueves, 12 de noviembre" */
export function diaLargo(fecha = new Date()) {
  return fecha.toLocaleDateString('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long'
  })
}

/** Muestra una fecha ISO en formato legible: "12 nov, 09:00". */
export function legible(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleString('es-CO', {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'
  })
}

/** Solo el dia: "12 nov". */
export function soloDia(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleDateString('es-CO', { day: '2-digit', month: 'short' })
}

/** Solo la hora: "9:00 a. m.". Sin cero delante, que es como se lee. */
export function soloHora(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' })
}

/**
 * Pone en plural la unidad de un insumo segun la cantidad.
 *
 * Sin esto se lee "2 frasco" y "48 unidad", que delata que el texto lo
 * armo una maquina. Las unidades del sistema son palabras corrientes, asi
 * que basta la regla general del español.
 */
export function cantidad(numero, unidad = '') {
  const n = Number(numero)
  const cifra = Number.isInteger(n) ? n : n.toFixed(2)
  if (!unidad) return String(cifra)
  if (n === 1) return `${cifra} ${unidad}`

  const u = unidad.toLowerCase()
  const plural = /[aeiouáéíóú]$/.test(u) ? u + 's'
    : /[zZ]$/.test(u) ? u.slice(0, -1) + 'ces'
      : u + 'es'
  return `${cifra} ${plural}`
}

/** Formatea un numero como pesos colombianos, sin decimales. */
export function pesos(valor) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency', currency: 'COP', maximumFractionDigits: 0
  }).format(valor || 0)
}

/** "hace 3 dias", "hace 2 meses". Para la ultima visita de una clienta. */
export function desdeHace(iso) {
  if (!iso) return 'sin registro'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const dias = Math.round((Date.now() - d.getTime()) / 86400000)
  if (dias <= 0) return 'hoy'
  if (dias === 1) return 'ayer'
  if (dias < 31) return `hace ${dias} días`
  const meses = Math.round(dias / 30)
  return meses === 1 ? 'hace un mes' : `hace ${meses} meses`
}

/** Las iniciales de un identificador, para mostrarlo sin enseñar el UUID. */
export function etiquetaCliente(id) {
  if (!id) return 'Clienta'
  return nombreDeCliente(id) || 'Clienta ' + String(id).slice(0, 4).toUpperCase()
}
