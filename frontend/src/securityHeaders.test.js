import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { CONTENT_SECURITY_POLICY, SECURITY_HEADERS } from '../security-headers.js'

const read = (path) => readFileSync(resolve(__dirname, '..', path), 'utf8')

describe('header di sicurezza del sito', () => {
  it('la CSP non ammette script o stili inline, eval, plugin o incorporamento in frame', () => {
    expect(CONTENT_SECURITY_POLICY).not.toContain('unsafe-inline')
    expect(CONTENT_SECURITY_POLICY).not.toContain('unsafe-eval')
    expect(CONTENT_SECURITY_POLICY).toContain("object-src 'none'")
    expect(CONTENT_SECURITY_POLICY).toContain("frame-ancestors 'none'")
    expect(CONTENT_SECURITY_POLICY).toContain("connect-src 'self'")
    expect(SECURITY_HEADERS['X-Content-Type-Options']).toBe('nosniff')
    expect(SECURITY_HEADERS['Referrer-Policy']).toBe('no-referrer')
  })

  // Se una pagina introducesse contenuto inline, la CSP lo bloccherebbe in produzione: meglio accorgersene qui.
  it.each(['index.html', 'public/offline.html'])('%s non contiene script, stili o gestori di eventi inline', (file) => {
    const html = read(file)
    const scripts = [...html.matchAll(/<script\b([^>]*)>/g)].map((m) => m[1])
    expect(scripts.every((attributes) => /\bsrc=/.test(attributes))).toBe(true)
    expect(html).not.toMatch(/<style\b/)
    expect(html).not.toMatch(/\sstyle=/)
    expect(html).not.toMatch(/\son[a-z]+=/)
  })
})
