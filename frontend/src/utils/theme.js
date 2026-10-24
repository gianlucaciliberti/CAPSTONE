// Tema chiaro/scuro. La scelta ('light', 'dark' o 'system') resta sul dispositivo (localStorage, non è un dato
// personale); 'system' segue la preferenza del telefono o del computer. Il tema si applica con l'attributo
// data-bs-theme sull'elemento <html>, quello che usa Bootstrap 5.3, e aggiorna il colore della barra del browser.

const STORAGE_KEY = 'valhalla-theme'
const CHOICES = ['light', 'dark', 'system']
const BAR_COLORS = { light: '#0e3b2c', dark: '#0a1813' }

const listeners = new Set()
const media = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null

function readChoice() {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return CHOICES.includes(value) ? value : 'system'
  } catch {
    return 'system'
  }
}

let choice = readChoice()

/** Tema effettivo, sempre 'light' o 'dark'. */
export function resolvedTheme(value = choice) {
  if (value === 'system') return media?.matches ? 'dark' : 'light'
  return value
}

/** Applica il tema al documento. Chiamata prima del primo render (main.jsx) per evitare un lampo di colore. */
export function applyTheme() {
  const theme = resolvedTheme()
  document.documentElement.setAttribute('data-bs-theme', theme)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', BAR_COLORS[theme])
  listeners.forEach((listener) => listener())
}

export function setThemeChoice(value) {
  choice = CHOICES.includes(value) ? value : 'system'
  try {
    if (choice === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, choice)
  } catch {
    // Archivio non disponibile (es. navigazione privata): il tema vale solo per questa visita.
  }
  applyTheme()
}

media?.addEventListener('change', () => {
  if (choice === 'system') applyTheme()
})

export const themeStore = {
  subscribe(listener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
  getChoice: () => choice,
  getResolved: () => resolvedTheme(),
}
