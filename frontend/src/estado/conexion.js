// Estado de conexion compartido por toda la aplicacion.
//
// Cualquier peticion reporta aqui si obtuvo respuesta. Si varias seguidas
// fallan, la aplicacion entra en modo "sin conexion" y lo indica de forma
// discreta en la barra lateral. En cuanto una funciona, vuelve a la normal.
//
// Se exige mas de un fallo seguido para cambiar de estado porque una sola
// peticion perdida no significa que el sistema este caido, y parpadear un
// aviso cada pocos segundos es peor que no mostrarlo.

const FALLOS_PARA_DARLO_POR_CAIDO = 2

let conectado = true
let fallosSeguidos = 0
const oyentes = new Set()

/** Lo llama el cliente GraphQL despues de cada peticion. */
export function reportar(huboRespuesta) {
  if (huboRespuesta) {
    fallosSeguidos = 0
    cambiar(true)
    return
  }

  fallosSeguidos += 1
  if (fallosSeguidos >= FALLOS_PARA_DARLO_POR_CAIDO) {
    cambiar(false)
  }
}

function cambiar(nuevo) {
  if (nuevo === conectado) return
  conectado = nuevo
  oyentes.forEach(avisar => avisar(conectado))
}

export function hayConexion() {
  return conectado
}

/** Suscribe a los cambios. Devuelve la funcion para darse de baja. */
export function alCambiar(oyente) {
  oyentes.add(oyente)
  return () => oyentes.delete(oyente)
}
