// Importi sempre in centesimi di euro, come nel backend: la virgola mobile serve solo per mostrarli.

const euroFormatter = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' })

export const formatEuro = (cents) => euroFormatter.format(cents / 100)

/** "15", "15,5" o "15.50" → 1550; null se non è un importo valido (massimo due decimali). */
export function parseEuro(value) {
  const match = /^(\d{1,5})(?:[.,](\d{1,2}))?$/.exec(value?.trim() ?? '')
  if (!match) return null
  return Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0'))
}
