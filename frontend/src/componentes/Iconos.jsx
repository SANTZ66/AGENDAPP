// Iconos dibujados a mano en SVG.
//
// Van escritos aqui y no desde una libreria de iconos para que la
// aplicacion no dependa de descargar nada: abre igual sin conexion.
// Todos heredan el color del texto y miden lo que diga su contenedor.

function Base({ children, tam = 18, ancho = 1.6 }) {
  return (
    <svg viewBox="0 0 24 24" width={tam} height={tam} fill="none"
         stroke="currentColor" strokeWidth={ancho}
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  )
}

export const IconoHoy = p => (
  <Base {...p}>
    <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
    <path d="M3.5 9.5h17M8 3v4M16 3v4" />
    <circle cx="12" cy="15" r="1.8" fill="currentColor" stroke="none" />
  </Base>
)

export const IconoAgenda = p => (
  <Base {...p}>
    <path d="M5 4.5h11l3.5 3.5v11.5a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5V6A1.5 1.5 0 0 1 5 4.5Z" />
    <path d="M8 11h8M8 15h5" />
  </Base>
)

export const IconoReservar = p => (
  <Base {...p}>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 7.5V12l3 2" />
  </Base>
)

export const IconoOperacion = p => (
  <Base {...p}>
    <path d="M4 19.5V13M9.3 19.5V8M14.7 19.5v-7M20 19.5V5.5" />
  </Base>
)

export const IconoClientas = p => (
  <Base {...p}>
    <circle cx="10" cy="8.5" r="3.5" />
    <path d="M3.8 19.5a6.4 6.4 0 0 1 12.4 0" />
    <path d="M17 8.2a3 3 0 0 1 0 5.6M18.6 19.5a6 6 0 0 0-1.4-3.6" />
  </Base>
)

export const IconoAlerta = p => (
  <Base {...p}>
    <path d="M12 8v4.5" />
    <circle cx="12" cy="16.2" r="1" fill="currentColor" stroke="none" />
    <path d="M10.3 4.2 2.6 17.6A1.9 1.9 0 0 0 4.3 20.5h15.4a1.9 1.9 0 0 0 1.7-2.9L13.7 4.2a1.9 1.9 0 0 0-3.4 0Z" />
  </Base>
)

export const IconoReintentar = p => (
  <Base {...p}>
    <path d="M20 12a8 8 0 1 1-2.6-5.9" />
    <path d="M20.5 4v4.5H16" />
  </Base>
)

export const IconoNube = p => (
  <Base {...p}>
    <path d="M7 18.5h10.2a3.8 3.8 0 0 0 .4-7.6 5.6 5.6 0 0 0-10.8-1.3A4.5 4.5 0 0 0 7 18.5Z" />
  </Base>
)

export const IconoMas = p => (
  <Base {...p}>
    <path d="M12 5.5v13M5.5 12h13" />
  </Base>
)

export const IconoFlecha = p => (
  <Base {...p}>
    <path d="M9 5.5 15.5 12 9 18.5" />
  </Base>
)
