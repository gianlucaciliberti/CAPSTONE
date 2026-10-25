import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import '@fontsource-variable/archivo/wdth.css'
import 'bootstrap/dist/css/bootstrap.min.css'
import './styles/theme.css'
import AuthProvider from './auth/AuthProvider'
import { router } from './router'
import { applyTheme } from './utils/theme'

// Tema applicato prima del primo render, così la pagina non lampeggia nel colore sbagliato.
applyTheme()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
)
