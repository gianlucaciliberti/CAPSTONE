import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import '@fontsource-variable/archivo/wdth.css'
import 'bootstrap/dist/css/bootstrap.min.css'
import './styles/theme.css'
import AuthProvider from './auth/AuthProvider'
import { registerServiceWorker } from './pwa'
import { router } from './router'
import { applyTheme } from './utils/theme'

// Tema applicato prima del primo render, così la pagina non lampeggia nel colore sbagliato.
applyTheme()

// Solo in produzione: in sviluppo la cache del service worker nasconderebbe le modifiche al codice.
if (import.meta.env.PROD) registerServiceWorker()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
)
