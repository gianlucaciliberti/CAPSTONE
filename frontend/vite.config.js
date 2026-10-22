import react from '@vitejs/plugin-react'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, loadEnv } from 'vite'
import { SECURITY_HEADERS } from './security-headers.js'

/** File statici sempre presenti nella cache dell'app, oltre a JavaScript e CSS della build. */
const STATIC_PRECACHE = [
  '/',
  '/offline.html',
  '/offline.css',
  '/manifest.webmanifest',
  '/icons/icon.svg',
  '/icons/icon-192.png',
]

/**
 * A fine build inserisce in dist/sw.js l'elenco dei file da mettere in cache e una versione calcolata dai nomi
 * (con hash) dei file: ogni deploy con codice diverso produce un service worker diverso, che il browser installa.
 */
function serviceWorkerManifest() {
  let outDir = 'dist'
  let assets = []
  return {
    name: 'service-worker-manifest',
    apply: 'build',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir)
    },
    generateBundle(_, bundle) {
      assets = Object.keys(bundle).filter((file) => file.startsWith('assets/') && !file.endsWith('.map'))
    },
    closeBundle() {
      const path = resolve(outDir, 'sw.js')
      const precache = [...STATIC_PRECACHE, ...assets.map((file) => `/${file}`)]
      const version = createHash('sha256').update(precache.join('\n')).digest('hex').slice(0, 12)
      const source = readFileSync(path, 'utf8')
        .replace("'__BUILD_VERSION__'", JSON.stringify(version))
        .replace('[] /* __PRECACHE__ */', JSON.stringify(precache))
      writeFileSync(path, source)
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendUrl = env.VITE_DEV_BACKEND_URL || 'http://localhost:8080'

  return {
    plugins: [react(), serviceWorkerManifest()],
    server: {
      port: 5173,
      // In sviluppo le chiamate /api passano dal dev server: stessa origine del frontend,
      // come in produzione con la rewrite di Render. I cookie HttpOnly funzionano senza CORS.
      proxy: {
        '/api': { target: backendUrl, changeOrigin: true },
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
