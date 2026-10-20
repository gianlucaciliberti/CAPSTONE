import { useState } from 'react'
import { ApiError } from '../api/client'
import { compactErrors } from './validation'

/** Stato comune dei form: valori, errori per campo, errore generale, invio in corso. */
export function useForm(initialValues) {
  const [values, setValues] = useState(initialValues)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const onChange = (event) => {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    setFieldErrors((current) => ({ ...current, [name]: null }))
  }

  /** Valida, invia e mostra gli errori del server (anche quelli sui singoli campi). */
  const submit = async (event, validate, action) => {
    event.preventDefault()
    setError(null)
    const errors = compactErrors(validate(values))
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    setLoading(true)
    try {
      await action(values)
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) setFieldErrors(err.fieldErrors)
      setError(err)
    } finally {
      setLoading(false)
    }
  }

  return { values, setValues, fieldErrors, error, loading, onChange, submit }
}
