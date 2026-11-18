// Traduccion de fallos tecnicos a frases que puede leer una persona.
//
// Regla de la casa: el usuario NUNCA ve el texto crudo de un error. Ni un
// codigo HTTP, ni un nombre de clase de Java, ni la palabra "gateway". Si
// algo se rompe, lee una frase que le dice que paso y que puede hacer.
//
// El detalle tecnico no se pierde: se escribe en la consola del navegador
// con console.warn, que es donde lo busca quien desarrolla. Separar las dos
// audiencias es justamente el punto.

const REGLAS = [
  {
    // La restriccion UNIQUE de PostgreSQL al reservar un cupo tomado.
    prueba: /409|conflict|unique|constraint|duplicate|ya (esta|fue) (tomad|reservad)/i,
    frase: 'Esa hora acaba de ocuparse. Elige otra del listado.'
  },
  {
    // Un microservicio apagado o reiniciandose. ConnectException, timeouts
    // y los 5xx que devuelve el gateway cuando no logra hablar con el.
    prueba: /connect|refused|unreachable|timeout|timed out|i\/o error|50[0-9]|unavailable|no route/i,
    frase: 'Esta parte del sistema no responde ahora mismo. Se reintenta sola.'
  },
  {
    prueba: /404|not found|no existe/i,
    frase: 'No encontramos ese registro. Puede que ya no exista.'
  },
  {
    prueba: /400|validation|obligatorio|must not|invalid|no puede ser/i,
    frase: 'Faltan datos o alguno no es válido. Revisa el formulario.'
  },
  {
    prueba: /401|403|forbidden|unauthorized/i,
    frase: 'No tienes permiso para hacer esto.'
  }
]

const POR_DEFECTO = 'No pudimos completar la acción. Inténtalo de nuevo.'

/**
 * Convierte cualquier error en una frase para el usuario.
 *
 * @param {unknown} crudo  lo que sea que devolvio el backend
 * @param {string} respaldo  frase a usar si ninguna regla encaja
 */
export function humanizar(crudo, respaldo = POR_DEFECTO) {
  const texto = extraer(crudo)
  if (!texto) return respaldo

  // El detalle tecnico va a la consola, no a la pantalla.
  console.warn('[detalle tecnico]', texto)

  for (const regla of REGLAS) {
    if (regla.prueba.test(texto)) return regla.frase
  }
  return respaldo
}

function extraer(crudo) {
  if (!crudo) return ''
  if (typeof crudo === 'string') return crudo
  if (Array.isArray(crudo)) return crudo.map(extraer).join(' | ')
  if (crudo.message) return String(crudo.message)
  try {
    return JSON.stringify(crudo)
  } catch {
    return String(crudo)
  }
}

/** Nombre en español de cada parte del sistema, para los textos de la interfaz. */
export const NOMBRE_DE_SECCION = {
  establecimientos: 'los salones',
  profesionales: 'el equipo',
  servicios: 'el catálogo de servicios',
  agenda: 'la agenda',
  disponibilidad: 'los cupos libres',
  inventario: 'el inventario',
  resumenIngresos: 'los ingresos',
  notificaciones: 'los avisos',
  clientesInactivos: 'las clientas por contactar'
}
