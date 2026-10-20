import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { authApi } from '../api/client'
import { useAuth } from '../auth/authContext'
import ErrorAlert from '../components/common/ErrorAlert'
import { AuthCard, FormField, SubmitButton } from '../components/common/Form'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { useForm } from '../utils/useForm'
import { emailError, passwordError, PASSWORD_HINT, requiredError } from '../utils/validation'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const form = useForm({ email: '', password: '' })

  const onSubmit = (event) =>
    form.submit(
      event,
      (v) => ({ email: emailError(v.email), password: requiredError(v.password, 'La password è obbligatoria.') }),
      async (v) => {
        await login(v.email, v.password)
        navigate(location.state?.from || '/', { replace: true })
      },
    )

  return (
    <AuthCard
      title="Accedi"
      subtitle="Entra nel gestionale della squadra."
      footer={
        <>
          Non hai un account? <Link to="/register">Registrati</Link>
        </>
      }
    >
      <ErrorAlert error={form.error} />
      {form.error?.code === 'ACCOUNT_PENDING' && (
        <p className="small">
          Non hai ricevuto l'email di conferma? <Link to="/verify-email">Richiedi un nuovo link</Link>.
        </p>
      )}
      <form onSubmit={onSubmit} noValidate>
        <FormField id="email" label="Email" type="email" autoComplete="email" value={form.values.email}
          onChange={form.onChange} error={form.fieldErrors.email} />
        <FormField id="password" label="Password" type="password" autoComplete="current-password"
          value={form.values.password} onChange={form.onChange} error={form.fieldErrors.password} />
        <SubmitButton loading={form.loading} loadingLabel="Accesso in corso…">
          Accedi
        </SubmitButton>
      </form>
      <p className="text-center small mt-3 mb-0">
        <Link to="/forgot-password">Password dimenticata?</Link>
      </p>
    </AuthCard>
  )
}

export function RegisterPage() {
  const [done, setDone] = useState(null)
  const form = useForm({ firstName: '', lastName: '', email: '', password: '', confirmPassword: '' })

  const onSubmit = (event) =>
    form.submit(
      event,
      (v) => ({
        firstName: requiredError(v.firstName, 'Il nome è obbligatorio.'),
        lastName: requiredError(v.lastName, 'Il cognome è obbligatorio.'),
        email: emailError(v.email),
        password: passwordError(v.password),
        confirmPassword: v.confirmPassword !== v.password ? 'Le password non coincidono.' : null,
      }),
      async ({ confirmPassword: _ignored, ...data }) => setDone(await authApi.register(data)),
    )

  if (done) {
    return (
      <AuthCard title="Registrazione inviata" footer={<Link to="/login">Vai al login</Link>}>
        <div className="alert alert-success">{done.message}</div>
        <p className="mb-0">
          Ti abbiamo inviato un'email: apri il link per <strong>confermare il tuo indirizzo</strong>. Senza conferma
          un amministratore non potrà approvare il tuo account.
        </p>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Registrati"
      subtitle="Dopo la registrazione un amministratore dovrà approvare il tuo account."
      footer={
        <>
          Hai già un account? <Link to="/login">Accedi</Link>
        </>
      }
    >
      <ErrorAlert error={form.error} />
      <form onSubmit={onSubmit} noValidate>
        <div className="row g-2">
          <div className="col-sm-6">
            <FormField id="firstName" label="Nome" autoComplete="given-name" value={form.values.firstName}
              onChange={form.onChange} error={form.fieldErrors.firstName} />
          </div>
          <div className="col-sm-6">
            <FormField id="lastName" label="Cognome" autoComplete="family-name" value={form.values.lastName}
              onChange={form.onChange} error={form.fieldErrors.lastName} />
          </div>
        </div>
        <FormField id="email" label="Email" type="email" autoComplete="email" value={form.values.email}
          onChange={form.onChange} error={form.fieldErrors.email} />
        <FormField id="password" label="Password" type="password" autoComplete="new-password"
          value={form.values.password} onChange={form.onChange} error={form.fieldErrors.password} hint={PASSWORD_HINT} />
        <FormField id="confirmPassword" label="Conferma password" type="password" autoComplete="new-password"
          value={form.values.confirmPassword} onChange={form.onChange} error={form.fieldErrors.confirmPassword} />
        <SubmitButton loading={form.loading} loadingLabel="Invio in corso…">
          Crea account
        </SubmitButton>
      </form>
    </AuthCard>
  )
}

export function ForgotPasswordPage() {
  const [done, setDone] = useState(null)
  const form = useForm({ email: '' })

  const onSubmit = (event) =>
    form.submit(event, (v) => ({ email: emailError(v.email) }), async (v) => setDone(await authApi.forgotPassword(v.email)))

  return (
    <AuthCard
      title="Password dimenticata"
      subtitle="Inserisci la tua email: ti invieremo un link per sceglierne una nuova."
      footer={<Link to="/login">Torna al login</Link>}
    >
      {done ? (
        <div className="alert alert-success mb-0">{done.message}</div>
      ) : (
        <>
          <ErrorAlert error={form.error} />
          <form onSubmit={onSubmit} noValidate>
            <FormField id="email" label="Email" type="email" autoComplete="email" value={form.values.email}
              onChange={form.onChange} error={form.fieldErrors.email} />
            <SubmitButton loading={form.loading} loadingLabel="Invio in corso…">
              Invia il link
            </SubmitButton>
          </form>
        </>
      )}
    </AuthCard>
  )
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [done, setDone] = useState(null)
  const form = useForm({ newPassword: '', confirmPassword: '' })

  const onSubmit = (event) =>
    form.submit(
      event,
      (v) => ({
        newPassword: passwordError(v.newPassword),
        confirmPassword: v.confirmPassword !== v.newPassword ? 'Le password non coincidono.' : null,
      }),
      async (v) => setDone(await authApi.resetPassword(token, v.newPassword)),
    )

  if (!token) {
    return (
      <AuthCard title="Link non valido" footer={<Link to="/forgot-password">Richiedi un nuovo link</Link>}>
        <p className="mb-0">Il link che hai aperto è incompleto. Richiedi un nuovo link di reimpostazione.</p>
      </AuthCard>
    )
  }

  return (
    <AuthCard title="Nuova password" footer={<Link to="/login">Torna al login</Link>}>
      {done ? (
        <>
          <div className="alert alert-success">{done.message}</div>
          <Link className="btn btn-primary w-100" to="/login">
            Accedi
          </Link>
        </>
      ) : (
        <>
          <ErrorAlert error={form.error} />
          {form.error?.code === 'INVALID_TOKEN' && (
            <p className="small">
              <Link to="/forgot-password">Richiedi un nuovo link</Link>
            </p>
          )}
          <form onSubmit={onSubmit} noValidate>
            <FormField id="newPassword" label="Nuova password" type="password" autoComplete="new-password"
              value={form.values.newPassword} onChange={form.onChange} error={form.fieldErrors.newPassword}
              hint={PASSWORD_HINT} />
            <FormField id="confirmPassword" label="Conferma nuova password" type="password" autoComplete="new-password"
              value={form.values.confirmPassword} onChange={form.onChange} error={form.fieldErrors.confirmPassword} />
            <SubmitButton loading={form.loading} loadingLabel="Salvataggio…">
              Salva la nuova password
            </SubmitButton>
          </form>
        </>
      )}
    </AuthCard>
  )
}

// Il link di verifica è monouso: in sviluppo React (StrictMode) esegue gli effetti due volte, quindi
// la richiesta per lo stesso token viene memorizzata e riusata invece di essere inviata di nuovo.
const verificationRequests = new Map()

function verifyOnce(token) {
  if (!verificationRequests.has(token)) verificationRequests.set(token, authApi.verifyEmail(token))
  return verificationRequests.get(token)
}

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [result, setResult] = useState(token ? { state: 'loading' } : { state: 'resend' })
  const [resent, setResent] = useState(null)
  const form = useForm({ email: '' })

  useEffect(() => {
    if (!token) return
    let active = true
    verifyOnce(token)
      .then((response) => active && setResult({ state: 'success', message: response.message }))
      .catch((error) => active && setResult({ state: 'error', error }))
    return () => {
      active = false
    }
  }, [token])

  const onResend = (event) =>
    form.submit(event, (v) => ({ email: emailError(v.email) }),
      async (v) => setResent(await authApi.resendVerification(v.email)))

  return (
    <AuthCard title="Conferma email" footer={<Link to="/login">Vai al login</Link>}>
      {result.state === 'loading' && <LoadingSpinner label="Conferma in corso…" />}
      {result.state === 'success' && (
        <div className="alert alert-success mb-0">
          {result.message} Un amministratore approverà presto il tuo account: riceverai un'email di conferma.
        </div>
      )}
      {result.state === 'error' && <ErrorAlert error={result.error} />}
      {result.state !== 'loading' && result.state !== 'success' && (
        resent ? (
          <div className="alert alert-success mb-0">{resent.message}</div>
        ) : (
          <>
            <p>Inserisci la tua email per ricevere un nuovo link di conferma.</p>
            <ErrorAlert error={form.error} />
            <form onSubmit={onResend} noValidate>
              <FormField id="email" label="Email" type="email" autoComplete="email" value={form.values.email}
                onChange={form.onChange} error={form.fieldErrors.email} />
              <SubmitButton loading={form.loading} loadingLabel="Invio in corso…">
                Invia un nuovo link
              </SubmitButton>
            </form>
          </>
        )
      )}
    </AuthCard>
  )
}
