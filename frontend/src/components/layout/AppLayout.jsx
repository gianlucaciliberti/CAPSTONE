import { LazyMotion, MotionConfig } from 'motion/react'
import * as m from 'motion/react-m'
import { Outlet, useLocation } from 'react-router'
import { useAuth } from '../../auth/authContext'
import AppNavbar, { BottomNav } from './AppNavbar'
import { AppStatusBanners } from './AppStatus'

/** Funzioni di animazione in un file separato, scaricato dopo il primo render. */
const loadMotionFeatures = () => import('./motionFeatures').then((module) => module.default)

export default function AppLayout() {
  const { status } = useAuth()
  const { pathname } = useLocation()
  const authenticated = status === 'authenticated'
  return (
    // reducedMotion="user": con "riduci movimento" attivo sul dispositivo le animazioni diventano istantanee.
    <MotionConfig reducedMotion="user">
      <LazyMotion features={loadMotionFeatures} strict>
        <div className={`d-flex flex-column min-vh-100${authenticated ? ' has-bottom-nav' : ''}`}>
          <AppNavbar />
          <AppStatusBanners />
          {/* Ogni cambio di pagina entra con un breve scivolamento: il contenuto parte già quasi visibile. */}
          <m.main key={pathname} className="container py-3 py-md-4 flex-grow-1"
            initial={{ opacity: 0.35, y: 8 }} animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}>
            <Outlet />
          </m.main>
          <footer className="app-footer py-3">
            <div className="container small text-secondary">Valhalla FC · gestionale della squadra</div>
          </footer>
          {authenticated && <BottomNav />}
        </div>
      </LazyMotion>
    </MotionConfig>
  )
}
