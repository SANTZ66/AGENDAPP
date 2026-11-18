// La "identidad" de quien reserva desde la version cliente.
//
// El sistema todavia no tiene inicio de sesion. Mientras tanto, cada
// navegador recuerda quien es la clienta (un identificador y su nombre) y
// ese nombre tambien se guarda en una libreta compartida, para que la
// version del establecimiento pueda mostrar "Camila Rojas" y no un codigo.
//
// Las dos versiones corren en el mismo navegador durante la presentacion,
// por eso basta con localStorage. Con autenticacion real, el nombre saldria
// del servicio de clientes y este archivo desapareceria.

const CLAVE_CLIENTE = 'citas.cliente'
const CLAVE_NOMBRES = 'citas.nombres'
const NOMBRE_PREDETERMINADO = 'Camila Rojas'

function leerJson(clave, respaldo) {
  try {
    const crudo = localStorage.getItem(clave)
    return crudo ? JSON.parse(crudo) : respaldo
  } catch {
    return respaldo
  }
}

function escribirJson(clave, valor) {
  try { localStorage.setItem(clave, JSON.stringify(valor)) } catch { /* sin almacenamiento */ }
}

/** La clienta de este navegador. Si no existe, se crea con un nombre de ejemplo. */
export function leerCliente() {
  const guardado = leerJson(CLAVE_CLIENTE, null)
  if (guardado?.id) return guardado
  const nuevo = { id: crypto.randomUUID(), nombre: NOMBRE_PREDETERMINADO }
  guardarCliente(nuevo)
  return nuevo
}

export function guardarCliente({ id, nombre }) {
  escribirJson(CLAVE_CLIENTE, { id, nombre })
  const libreta = leerJson(CLAVE_NOMBRES, {})
  libreta[id] = nombre
  escribirJson(CLAVE_NOMBRES, libreta)
}

/** El nombre registrado para un identificador, o null si no se conoce. */
export function nombreDeCliente(id) {
  return leerJson(CLAVE_NOMBRES, {})[id] || null
}
