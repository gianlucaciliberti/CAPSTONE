import { describe, expect, it } from 'vitest'
import {
  addMonths,
  buildMonthGrid,
  currentTeamMonth,
  daysFromToday,
  formatDaysFromToday,
  formatMonthTitle,
  formatPlainDate,
  parseMonth,
  teamDateKey,
  toLocalInputValue,
} from './dates'

describe('dates (fuso Europe/Rome)', () => {
  it("usa il giorno italiano anche quando in UTC è ancora il giorno precedente", () => {
    // 1/11 alle 00:30 a Roma = 31/10 alle 23:30 UTC
    expect(teamDateKey('2026-10-31T23:30:00Z')).toBe('2026-11-01')
    expect(currentTeamMonth(new Date('2026-10-31T23:30:00Z'))).toBe('2026-11')
  })

  it("converte l'istante nel valore dell'input datetime-local in ora italiana, estate e inverno", () => {
    expect(toLocalInputValue('2026-11-07T15:30:00+01:00')).toBe('2026-11-07T15:30')
    expect(toLocalInputValue('2026-06-15T19:00:00Z')).toBe('2026-06-15T21:00')
  })

  it('conta i giorni di calendario italiani fino alla partita', () => {
    const now = new Date('2026-10-31T22:30:00Z') // 23:30 di sabato 31 a Roma
    expect(formatDaysFromToday('2026-10-31T23:30:00Z', now)).toBe('domani') // 00:30 di domenica 1/11
    expect(formatDaysFromToday('2026-10-31T21:00:00Z', now)).toBe('oggi')
    expect(formatDaysFromToday('2026-11-05T19:00:00Z', now)).toBe('tra 5 giorni')
    expect(daysFromToday('2026-10-30T19:00:00Z', now)).toBe(-1)
  })

  it('formatta le date senza ora così come sono, senza spostarle di giorno', () => {
    expect(formatPlainDate('2026-11-01')).toBe('1 nov 2026')
  })

  it('costruisce la griglia del mese partendo dal lunedì', () => {
    // Novembre 2026 inizia di domenica: la prima riga parte da lunedì 26 ottobre.
    const grid = buildMonthGrid({ year: 2026, month: 11 })
    expect(grid[0][0]).toEqual({ key: '2026-10-26', day: 26, inMonth: false })
    expect(grid[0][6]).toEqual({ key: '2026-11-01', day: 1, inMonth: true })
    expect(grid.flat().filter((d) => d.inMonth)).toHaveLength(30)
    expect(grid.every((week) => week.length === 7)).toBe(true)
  })

  it('gestisce cambio anno e formato del mese', () => {
    expect(addMonths({ year: 2026, month: 12 }, 1)).toEqual({ year: 2027, month: 1 })
    expect(addMonths({ year: 2026, month: 1 }, -1)).toEqual({ year: 2025, month: 12 })
    expect(formatMonthTitle({ year: 2026, month: 11 })).toBe('Novembre 2026')
    expect(parseMonth('2026-13')).toBeNull()
    expect(parseMonth('2026-02')).toEqual({ year: 2026, month: 2 })
  })
})
