// Permite a un componente de React enterarse de si hay conexion.
//
// El estado vive fuera de React, en estado/conexion.js, porque lo escribe
// el cliente GraphQL, que no es un componente. Este gancho solo se suscribe
// a sus cambios y provoca un renderizado cuando ocurren.

import { useEffect, useState } from 'react'
import { alCambiar, hayConexion } from '../estado/conexion.js'

export function useConexion() {
  const [conectado, setConectado] = useState(hayConexion())
  useEffect(() => alCambiar(setConectado), [])
  return conectado
}
