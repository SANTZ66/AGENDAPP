// El gancho que usan todas las pantallas para pedir datos.
//
// Resuelve tres cosas que, juntas, hacen que apagar un microservicio no se
// note en la pantalla:
//
// 1. Conserva lo ultimo bueno. Si el inventario dejo de responder, se sigue
//    mostrando la ultima lectura que si llego, con una nota discreta de
//    cuando fue. Es informacion real que estuvo vigente hace un minuto, y es
//    mucho mas util que un hueco vacio.
//
// 2. Degrada por campo, no por pantalla. Una consulta puede traer los
//    ingresos y fallar el inventario. Solo se marca el panel afectado.
//
// 3. Reintenta sola, espaciando cada vez mas. Cuando el servicio vuelve, la
//    pantalla se arregla sin que nadie recargue nada.

import { useCallback, useEffect, useRef, useState } from 'react'
import { consultar } from '../api/graphql.js'
import { alReservar } from '../api/canal.js'

const ESPERAS_MS = [2000, 4000, 8000, 15000]

/**
 * @param {string} consulta       el texto GraphQL
 * @param {object} variables      valores de la consulta
 * @param {object} opciones
 * @param {boolean} opciones.activo    si es false no pide nada
 * @param {number}  opciones.intervalo refresco periodico en milisegundos
 */
export function useConsulta(consulta, variables, opciones = {}) {
  const { activo = true, intervalo = 0 } = opciones

  const [datos, setDatos] = useState(null)
  const [desactualizados, setDesactualizados] = useState([])
  const [vacios, setVacios] = useState([])
  const [cargandoPrimera, setCargandoPrimera] = useState(true)
  const [actualizadoEn, setActualizadoEn] = useState(null)

  // Las referencias guardan valores entre renderizados sin provocar uno
  // nuevo al cambiarlas. Aqui hacen falta para no reiniciar los temporizadores
  // en cada vuelta.
  const ultimosBuenos = useRef({})
  const intentosFallidos = useRef(0)
  const temporizador = useRef(null)
  const montado = useRef(true)

  // Las variables se comparan por su contenido y no por identidad: un objeto
  // nuevo con los mismos valores no debe disparar otra peticion.
  const firma = JSON.stringify(variables || {})

  const traer = useCallback(async () => {
    if (!activo) {
      setCargandoPrimera(false)
      return
    }

    const resultado = await consultar(consulta, JSON.parse(firma))
    if (!montado.current) return

    if (resultado.modo === 'sin-servidor') {
      // No llego nada. Se deja en pantalla lo ultimo bueno y se marca como
      // desactualizado todo lo que ya se tenia.
      const conocidos = Object.keys(ultimosBuenos.current)
      setDesactualizados(conocidos)
      setVacios(conocidos.length === 0 ? ['todo'] : [])
      programarReintento()
      setCargandoPrimera(false)
      return
    }

    const llegaron = resultado.datos || {}
    const caidos = resultado.caidos || []
    const mezcla = { ...ultimosBuenos.current }
    const ahoraDesactualizados = []
    const ahoraVacios = []

    for (const campo of Object.keys(llegaron)) {
      const valor = llegaron[campo]
      const fallo = caidos.includes(campo) || valor === null || valor === undefined

      if (!fallo) {
        mezcla[campo] = valor
        continue
      }

      // Fallo: si teniamos un valor previo lo mantenemos y lo marcamos;
      // si nunca tuvimos nada, el panel se declara no disponible.
      if (campo in ultimosBuenos.current) {
        ahoraDesactualizados.push(campo)
      } else {
        ahoraVacios.push(campo)
      }
    }

    ultimosBuenos.current = mezcla
    setDatos(mezcla)
    setDesactualizados(ahoraDesactualizados)
    setVacios(ahoraVacios)
    setCargandoPrimera(false)

    if (caidos.length === 0) {
      setActualizadoEn(new Date())
      intentosFallidos.current = 0
      programarRefresco()
    } else {
      programarReintento()
    }
  }, [consulta, firma, activo])

  function limpiar() {
    if (temporizador.current) {
      clearTimeout(temporizador.current)
      temporizador.current = null
    }
  }

  function programarRefresco() {
    limpiar()
    if (intervalo > 0) {
      temporizador.current = setTimeout(() => traer(), intervalo)
    }
  }

  // Espera creciente: 2s, 4s, 8s y a partir de ahi cada 15s. Insistir cada
  // segundo contra un servicio caido solo llena los registros de ruido.
  function programarReintento() {
    limpiar()
    const i = Math.min(intentosFallidos.current, ESPERAS_MS.length - 1)
    const espera = ESPERAS_MS[i]
    intentosFallidos.current += 1
    temporizador.current = setTimeout(() => traer(), espera)
  }

  useEffect(() => {
    montado.current = true
    intentosFallidos.current = 0
    traer()
    return () => { montado.current = false; limpiar() }
  }, [traer])

  // Si alguien reserva desde otra ventana, todo lo que esta en pantalla se
  // vuelve a pedir al instante en vez de esperar al siguiente refresco.
  useEffect(() => alReservar(() => { intentosFallidos.current = 0; traer() }), [traer])

  // Cuando el navegador recupera la red, no tiene sentido seguir esperando
  // el siguiente reintento programado.
  useEffect(() => {
    function alVolver() { intentosFallidos.current = 0; traer() }
    window.addEventListener('online', alVolver)
    return () => window.removeEventListener('online', alVolver)
  }, [traer])

  return {
    datos: datos || {},
    desactualizados,
    vacios,
    cargandoPrimera,
    actualizadoEn,
    refrescar: () => { intentosFallidos.current = 0; traer() }
  }
}
