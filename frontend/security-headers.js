// Header di sicurezza del sito statico (frontend). Fonte unica: li usa "vite preview" (vite.config.js) per provare
// la build in locale; in produzione vanno impostati identici sul sito statico di Render (vedi docs/deployment.md).
// Non si applicano a "vite dev": il dev server inserisce script inline per l'aggiornamento a caldo.

/**
 * Content Security Policy: solo risorse dello stesso sito. Niente script o stili inline (index.html e offline.html
 * usano solo file esterni); le API sono sulla stessa origine grazie alla rewrite /api/*. "data:" nelle immagini serve
 * alle icone SVG incorporate nel CSS di Bootstrap (frecce delle select, spunte delle checkbox).
 */
export const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self'",
  "img-src 'self' data:",
  "font-src 'self'",
  "connect-src 'self'",
  "manifest-src 'self'",
  "worker-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ')

export const SECURITY_HEADERS = {
  'Content-Security-Policy': CONTENT_SECURITY_POLICY,
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
}
