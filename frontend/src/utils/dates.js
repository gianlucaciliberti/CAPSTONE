// Date e orari della squadra: sempre mostrati nel fuso Europe/Rome, qualunque sia il fuso del dispositivo.

export const TEAM_TIME_ZONE = 'Europe/Rome'

const dateTimeFormatter = new Intl.DateTimeFormat('it-IT', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: TEAM_TIME_ZONE,
})
const longDateFormatter = new Intl.DateTimeFormat('it-IT', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: TEAM_TIME_ZONE,
})
const timeFormatter = new Intl.DateTimeFormat('it-IT', { hour: '2-digit', minute: '2-digit', timeZone: TEAM_TIME_ZONE })
// en-CA produce date nel formato ISO "2026-11-07"
const isoDateFormatter = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  timeZone: TEAM_TIME_ZONE,
})
const isoPartsFormatter = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: TEAM_TIME_ZONE,
})

const plainDateFormatter = new Intl.DateTimeFormat('it-IT', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

export const formatDateTime = (iso) => dateTimeFormatter.format(new Date(iso))
/** Data senza ora ("2026-11-07"), es. una scadenza: formattata così com'è, senza conversioni di fuso. */
export const formatPlainDate = (date) => plainDateFormatter.format(new Date(`${date}T00:00:00Z`))
export const formatLongDate = (iso) => longDateFormatter.format(new Date(iso))
export const formatTime = (iso) => timeFormatter.format(new Date(iso))

/** Giorno di calendario (YYYY-MM-DD) dell'istante, nel fuso della squadra. */
export const teamDateKey = (iso) => isoDateFormatter.format(new Date(iso))

/** Giorni di calendario (fuso della squadra) tra oggi e l'istante: 0 = oggi, 1 = domani, negativo = passato. */
export function daysFromToday(iso, now = new Date()) {
  const toUtcDay = (key) => Date.UTC(Number(key.slice(0, 4)), Number(key.slice(5, 7)) - 1, Number(key.slice(8, 10)))
  return Math.round((toUtcDay(teamDateKey(iso)) - toUtcDay(teamDateKey(now))) / 86400000)
}

/** "oggi", "domani" o "tra N giorni". */
export function formatDaysFromToday(iso, now = new Date()) {
  const days = daysFromToday(iso, now)
  if (days <= 0) return 'oggi'
  return days === 1 ? 'domani' : `tra ${days} giorni`
}

/** Valore per un input datetime-local ("YYYY-MM-DDTHH:mm") con l'ora italiana dell'istante. */
export function toLocalInputValue(iso) {
  const parts = Object.fromEntries(isoPartsFormatter.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

/** Mese corrente nel fuso della squadra, come "YYYY-MM". */
export const currentTeamMonth = (now = new Date()) => teamDateKey(now).slice(0, 7)

export function parseMonth(value) {
  const match = /^(\d{4})-(\d{2})$/.exec(value ?? '')
  if (!match) return null
  const month = Number(match[2])
  return month >= 1 && month <= 12 ? { year: Number(match[1]), month } : null
}

export const monthKey = ({ year, month }) => `${year}-${String(month).padStart(2, '0')}`

export function addMonths({ year, month }, delta) {
  const index = year * 12 + (month - 1) + delta
  return { year: Math.floor(index / 12), month: (index % 12) + 1 }
}

export function formatMonthTitle({ year, month }) {
  const title = new Intl.DateTimeFormat('it-IT', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(Date.UTC(year, month - 1, 1)),
  )
  return title.charAt(0).toUpperCase() + title.slice(1)
}

export const WEEKDAYS = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom']

/**
 * Griglia del mese a settimane (lunedì-domenica), con i giorni dei mesi adiacenti per completare le righe.
 * I calcoli usano date UTC "pure", così non dipendono dal fuso del dispositivo.
 */
export function buildMonthGrid({ year, month }) {
  const first = new Date(Date.UTC(year, month - 1, 1))
  const offset = (first.getUTCDay() + 6) % 7 // lunedì = 0
  const start = new Date(Date.UTC(year, month - 1, 1 - offset))
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const weeks = Math.ceil((offset + daysInMonth) / 7)
  return Array.from({ length: weeks }, (_, w) =>
    Array.from({ length: 7 }, (_, d) => {
      const date = new Date(start.getTime() + (w * 7 + d) * 86400000)
      return {
        key: date.toISOString().slice(0, 10),
        day: date.getUTCDate(),
        inMonth: date.getUTCMonth() === month - 1,
      }
    }),
  )
}
