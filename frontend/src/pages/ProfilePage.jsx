import { useState } from 'react'
import { ApiError, authApi } from '../api/client'
import { ROLE_LABELS, useAuth } from '../auth/authContext'
import ErrorAlert from '../components/common/ErrorAlert'
import { FormField, SubmitButton } from '../components/common/Form'
import { compactErrors, passwordError, PASSWORD_HINT, requiredError } from '../utils/validation'

const EMPTY = { currentPassword: '', newPassword: '', confirmPassword: '' }

export default function ProfilePage() {
  const { user, setSession } = useAuth()
  const [values, setValues] = useState(EMPTY)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const onChange = (event) => {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    setFieldErrors((current) => ({ ...current, [name]: null }))
  }

  const onSubmit = async (event) => {
    event.preventDefault()
    setError(null)
    setSuccess(false)
    const errors = compactErrors({
      currentPassword: requiredError(values.currentPassword, 'Inserisci la password attuale.'),
      newPassword: passwordError(values.newPassword),
      confirmPassword: values.confirmPassword !== values.newPassword ? 'Le password non coincidono.' : null,
    })
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    setLoading(true)
    try {
      // Il backend chiude tutte le sessioni e ne apre una nuova per questo dispositivo.
      setSession(await authApi.changePassword(values.currentPassword, values.newPassword))
      setValues(EMPTY)
      setSuccess(true)
    } catch (err) {
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setError(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="row g-4">
      <div className="col-lg-5">
        <div className="card shadow-sm border-0 h-100">
          <div className="card-body">
            <h1 className="h4 mb-3">Profilo</h1>
            <dl className="row mb-0">
              <dt className="col-4">Nome</dt>
              <dd className="col-8">
                {user.firstName} {user.lastName}
              </dd>
              <dt className="col-4">Email</dt>
              <dd className="col-8 text-break">
                {user.email}{' '}
                {user.emailVerified ? (
                  <span className="badge text-bg-success">verificata</span>
                ) : (
                  <span className="badge text-bg-warning">da verificare</span>
                )}
              </dd>
              <dt className="col-4">Ruolo</dt>
              <dd className="col-8 mb-0">{ROLE_LABELS[user.role]}</dd>
            </dl>
          </div>
        </div>
      </div>
      <div className="col-lg-7">
        <div className="card shadow-sm border-0">
          <div className="card-body">
            <h2 className="h5 mb-3">Cambia password</h2>
            {success && (
              <div className="alert alert-success" role="status">
                Password aggiornata. Le sessioni aperte su altri dispositivi sono state chiuse.
              </div>
            )}
            <ErrorAlert error={error} />
            <form onSubmit={onSubmit} noValidate>
              <FormField id="currentPassword" label="Password attuale" type="password" autoComplete="current-password"
                value={values.currentPassword} onChange={onChange} error={fieldErrors.currentPassword} />
              <FormField id="newPassword" label="Nuova password" type="password" autoComplete="new-password"
                value={values.newPassword} onChange={onChange} error={fieldErrors.newPassword} hint={PASSWORD_HINT} />
              <FormField id="confirmPassword" label="Conferma nuova password" type="password"
                autoComplete="new-password" value={values.confirmPassword} onChange={onChange}
                error={fieldErrors.confirmPassword} />
              <SubmitButton loading={loading} loadingLabel="Salvataggio…" className="btn btn-primary">
                Aggiorna password
              </SubmitButton>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}
