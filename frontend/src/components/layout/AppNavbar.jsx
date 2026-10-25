import { CalendarDays, Hand, House, Monitor, Moon, Sun, Trophy, Wallet } from 'lucide-react'
import * as m from 'motion/react-m'
import { useState, useSyncExternalStore } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { ROLE_LABELS, useAuth } from '../../auth/authContext'
import { setThemeChoice, themeStore } from '../../utils/theme'

/** Sezioni principali, sempre a portata di pollice nella barra inferiore su smartphone e tablet. */
const BOTTOM_LINKS = [
  { to: '/', label: 'Home', icon: House, end: true },
  { to: '/calendar', label: 'Calendario', icon: CalendarDays },
  { to: '/availability', label: 'Disponibilità', icon: Hand },
  { to: '/results', label: 'Risultati', icon: Trophy },
  { to: '/payments', label: 'Pagamenti', icon: Wallet },
]

const THEMES = {
  light: { label: 'chiaro', next: 'dark', icon: Sun },
  dark: { label: 'scuro', next: 'system', icon: Moon },
  system: { label: 'come il dispositivo', next: 'light', icon: Monitor },
}

/** Barra di navigazione inferiore per gli utenti autenticati; nascosta da desktop (classe CSS app-bottom-nav). */
export function BottomNav() {
  return (
    <nav className="app-bottom-nav" aria-label="Navigazione principale">
      {BOTTOM_LINKS.map(({ to, label, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end}>
          {({ isActive }) => (
            <>
              {isActive && (
                <m.span className="nav-indicator" layoutId="bottom-nav-indicator" aria-hidden="true"
                  transition={{ type: 'spring', stiffness: 520, damping: 40 }} />
              )}
              <Icon className="icon" strokeWidth={isActive ? 2.25 : 1.75} aria-hidden="true" />
              {label}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}

/** Tema chiaro, scuro o come il dispositivo: un tocco passa al successivo. */
export function ThemeToggle() {
  const choice = useSyncExternalStore(themeStore.subscribe, themeStore.getChoice)
  const current = THEMES[choice]
  const Icon = current.icon
  return (
    <button type="button" className="btn btn-sm theme-toggle" onClick={() => setThemeChoice(current.next)}
      aria-label={`Tema ${current.label}. Passa al tema ${THEMES[current.next].label}`}
      title={`Tema ${current.label}`}>
      <Icon size={18} aria-hidden="true" />
    </button>
  )
}

export default function AppNavbar() {
  const { status, user, hasRole, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  // Le sezioni principali sono nella barra inferiore su mobile: nel menu in alto compaiono solo da desktop.
  const links = [{ to: '/', label: 'Home', end: true, primary: true }]
  if (status === 'authenticated') {
    BOTTOM_LINKS.slice(1).forEach((link) => links.push({ ...link, primary: true }))
    if (hasRole('COACH')) links.push({ to: '/emails', label: 'Email' })
    if (hasRole('ADMIN')) links.push({ to: '/admin/users', label: 'Utenti' })
    links.push({ to: '/profile', label: 'Profilo' })
  }

  const onLogout = async () => {
    setOpen(false)
    await logout()
    navigate('/login')
  }

  return (
    <nav className="navbar navbar-expand-lg navbar-dark app-navbar">
      <div className="container">
        <Link className="navbar-brand brand-wordmark" to="/" aria-label="Valhalla FC, home">
          Valhalla <span>FC</span>
        </Link>
        <div className="d-flex align-items-center gap-2 order-lg-last ms-auto ms-lg-3">
          <ThemeToggle />
          <button
            className="navbar-toggler"
            type="button"
            aria-controls="main-navbar"
            aria-expanded={open}
            aria-label="Apri il menu di navigazione"
            onClick={() => setOpen((value) => !value)}
          >
            <span className="navbar-toggler-icon" />
          </button>
        </div>
        <div className={`collapse navbar-collapse${open ? ' show' : ''}`} id="main-navbar">
          <ul className="navbar-nav me-auto">
            {links.map((link) => (
              <li className={`nav-item${link.primary ? ' d-none d-lg-block' : ''}`} key={link.to}>
                <NavLink className="nav-link" to={link.to} end={link.end} onClick={() => setOpen(false)}>
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
          {status === 'authenticated' && (
            <div className="d-flex align-items-center gap-2 text-white">
              <span className="small">
                {user.firstName} <span className="opacity-75">· {ROLE_LABELS[user.role]}</span>
              </span>
              <button type="button" className="btn btn-sm btn-outline-light" onClick={onLogout}>
                Esci
              </button>
            </div>
          )}
          {status === 'anonymous' && (
            <div className="d-flex gap-2">
              <Link className="btn btn-sm btn-primary" to="/login" onClick={() => setOpen(false)}>
                Accedi
              </Link>
              <Link className="btn btn-sm btn-outline-light" to="/register" onClick={() => setOpen(false)}>
                Registrati
              </Link>
            </div>
          )}
        </div>
      </div>
    </nav>
  )
}
