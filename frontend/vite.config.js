import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { SECURITY_HEADERS } from './security-headers.js'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendUrl = env.VITE_DEV_BACKEND_URL || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      // In sviluppo le chiamate /api passano dal dev server: stessa origine del frontend,
      // come in produzione con la rewrite di Render. I cookie HttpOnly funzionano senza CORS.
      // Il proxy toglie l'header Origin: per il backend la richiesta arriva dalla stessa origine (come in produzione
      // con la rewrite di Render), quindi funziona su qualsiasi porta locale, anche con "npm run preview".
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          configure: (proxy) => proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
        },
      },
    },
    // "npm run preview" serve la build con gli stessi header di sicurezza della produzione (e lo stesso proxy /api).
    preview: {
      headers: SECURITY_HEADERS,
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.js',
    },
  }
})
