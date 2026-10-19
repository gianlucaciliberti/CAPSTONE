// Controlli lato client: servono solo a dare un riscontro immediato. Le stesse regole sono verificate dal backend.

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function emailError(value) {
  if (!value?.trim()) return "L'email è obbligatoria."
  if (!EMAIL_PATTERN.test(value.trim())) return 'Inserisci un indirizzo email valido.'
  return null
}

export function requiredError(value, message) {
  return value?.trim() ? null : message
}

/** Stesse regole del backend (@StrongPassword): almeno 10 caratteri, una lettera e un numero, max 72 byte. */
export function passwordError(value) {
  if (!value) return 'La password è obbligatoria.'
  if (new TextEncoder().encode(value).length > 72) return 'La password è troppo lunga (massimo 72 caratteri).'
  if ([...value].length < 10 || !/\p{L}/u.test(value) || !/\p{N}/u.test(value)) {
    return 'La password deve avere almeno 10 caratteri, con almeno una lettera e un numero.'
  }
  return null
}

export const PASSWORD_HINT = 'Almeno 10 caratteri, con almeno una lettera e un numero.'

/** Rimuove le chiavi senza errore: un oggetto vuoto significa form valido. */
export function compactErrors(errors) {
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => message))
}
