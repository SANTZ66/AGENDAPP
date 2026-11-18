// Permite a un componente de React enterarse de la sesion del establecimiento.
//
// El estado vive fuera de React, en estado/sesion.js, porque lo escribe el
// cliente GraphQL, que no es un componente. Este gancho solo se suscribe a
// sus cambios y provoca un renderizado cuando ocurren.
//
// Devuelve la sesion vigente ({ token, expiraEn, nombre, establecimientoId })
// o null si no hay ninguna o ya vencio.

import { useEffect, useState } from 'react'
import { alCambiar, leer, vigente } from '../estado/sesion.js'

function actual() {
  return vigente() ? leer() : null
}

export function useSesion() {
  const [sesion, setSesion] = useState(actual)
  useEffect(() => alCambiar(() => setSesion(actual())), [])
  return sesion
}
