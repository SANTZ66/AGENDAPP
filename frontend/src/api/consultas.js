// Las consultas y mutaciones que usa la aplicacion, en un solo lugar.
//
// Tenerlas juntas y con nombre facilita ver de un vistazo que le pide el
// frontend al backend, y evita repetir el mismo texto en varias pantallas.

export const ESTABLECIMIENTOS = `
  query {
    establecimientos { id nombre telefono }
  }`

export const PROFESIONALES = `
  query($est: ID!) {
    profesionales(establecimientoId: $est) { id nombre activo }
  }`

export const SERVICIOS = `
  query($est: ID!) {
    servicios(establecimientoId: $est) {
      id nombre duracionMinutos precio periodicidadDias
    }
  }`

/**
 * La consulta del panel de operacion.
 *
 * Es el mejor ejemplo de para que sirve el gateway: una sola peticion que
 * por dentro resuelve llamadas a ms-operaciones y ms-relacion-cliente.
 */
export const OPERACION = `
  query($est: ID!, $desde: String!, $hasta: String!) {
    resumenIngresos(establecimientoId: $est, desde: $desde, hasta: $hasta) {
      serviciosPrestados
      totalIngresos
    }
    inventario(establecimientoId: $est) {
      id nombre unidad existencia umbralMinimo enUmbral
    }
  }`

/**
 * La agenda del profesional.
 *
 * El campo "servicio" trae el NOMBRE, no el identificador. Ese nombre vive
 * en ms-establecimientos y la cita en ms-agenda: el gateway junta ambos y
 * el frontend recibe el dato ya resuelto.
 */
export const AGENDA = `
  query($prof: ID!, $desde: String!, $hasta: String!) {
    agenda(profesionalId: $prof, desde: $desde, hasta: $hasta) {
      id inicio fin estado clienteId servicio duracionMinutos valorCobrado confirmadaEn
    }
  }`

export const DISPONIBILIDAD = `
  query($prof: ID!, $serv: ID!, $fecha: String!) {
    disponibilidad(profesionalId: $prof, servicioId: $serv, fecha: $fecha) {
      inicio fin
    }
  }`

export const NOTIFICACIONES = `
  query($est: ID!) {
    notificaciones(establecimientoId: $est) {
      id citaId tipo canal asunto cuerpo estado creadaEn
    }
  }`

export const CLIENTES_INACTIVOS = `
  query($est: ID!) {
    clientesInactivos(establecimientoId: $est) {
      clienteId ultimaVisita proximoContacto totalVisitas totalCancelaciones
    }
  }`

/**
 * La pantalla de inicio, en una sola peticion.
 *
 * Pide tres cosas que viven en tres microservicios distintos: las citas del
 * dia en ms-agenda, los ingresos en ms-operaciones y el inventario tambien
 * en ms-operaciones. El gateway las resuelve en paralelo y devuelve una sola
 * respuesta.
 *
 * Es ademas la mejor demostracion de la tolerancia a fallos: si se apaga
 * uno de los servicios, los campos que ese servicio resolvia vuelven en null
 * y el resto llega intacto. La pantalla degrada solo esa parte.
 */
export const HOY = `
  query($prof: ID!, $est: ID!, $desde: String!, $hasta: String!) {
    agenda(profesionalId: $prof, desde: $desde, hasta: $hasta) {
      id inicio fin estado clienteId servicio duracionMinutos valorCobrado confirmadaEn
    }
    resumenIngresos(establecimientoId: $est, desde: $desde, hasta: $hasta) {
      serviciosPrestados
      totalIngresos
    }
    inventario(establecimientoId: $est) {
      id nombre unidad existencia umbralMinimo enUmbral
    }
  }`

// --- Mutaciones ---

/** Abre sesion en el panel del establecimiento. Devuelve el token. */
export const LOGIN = `
  mutation($correo: String!, $clave: String!) {
    login(correo: $correo, clave: $clave) {
      token expiraEn nombre establecimientoId rol
    }
  }`

export const RESERVAR = `
  mutation($est: ID!, $prof: ID!, $serv: ID!, $cli: ID!, $inicio: String!, $fin: String!) {
    reservarCita(establecimientoId: $est, profesionalId: $prof, servicioId: $serv,
                 clienteId: $cli, inicio: $inicio, fin: $fin) {
      id inicio fin estado
    }
  }`

export const CERRAR = `
  mutation($id: ID!, $valor: Float!) {
    cerrarCita(citaId: $id, valorCobrado: $valor) { id estado valorCobrado }
  }`

export const CONFIRMAR = `
  mutation($id: ID!) {
    confirmarCita(citaId: $id) { id estado confirmadaEn }
  }`

export const CANCELAR = `
  mutation($id: ID!, $por: String!, $motivo: String) {
    cancelarCita(citaId: $id, canceladaPor: $por, motivo: $motivo) { id estado }
  }`

/**
 * La agenda de TODOS los profesionales de un establecimiento en una sola
 * peticion. Cada profesional va con un alias (p0, p1...) para que GraphQL
 * devuelva una lista por cada uno.
 *
 * Sirve para dos cosas: que el establecimiento note una reserva nueva aunque
 * sea con otra profesional, y que la clienta vea todas sus citas.
 */
export function agendaDeTodos(profesionales) {
  if (!profesionales.length) return ''
  const campos = profesionales.map((p, i) =>
    `p${i}: agenda(profesionalId: "${p.id}", desde: $desde, hasta: $hasta) {
      id inicio fin estado clienteId servicio duracionMinutos valorCobrado confirmadaEn
    }`).join('\n')
  return `query($desde: String!, $hasta: String!) { ${campos} }`
}
