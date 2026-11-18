import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import './estilos.css'
import './roles.css'

// Punto de entrada. React toma el div con id "root" del index.html y
// dibuja dentro de el toda la aplicacion.
//
// BrowserRouter es lo que permite tener varias pantallas con direcciones
// distintas (/cliente, /negocio...) sin recargar la pagina.
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
)
