import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo (npm run dev) el frontend corre en el puerto 5173 y el
// gateway en el 8000. Para el navegador son origenes distintos, asi que
// toda peticion seria bloqueada por CORS.
//
// El proxy evita el problema: la aplicacion siempre llama a "/graphql"
// (una ruta relativa, mismo origen) y Vite reenvia esa peticion al
// gateway. En produccion hace lo mismo nginx. Asi el codigo no necesita
// saber donde vive el backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/graphql': {
        target: 'http://localhost:8000',
        changeOrigin: true
      }
    }
  }
})
