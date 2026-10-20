import { createBrowserRouter } from 'react-router'
import { RequireAnonymous, RequireAuth } from './auth/RequireAuth'
import AppLayout from './components/layout/AppLayout'
import AdminUsersPage from './pages/AdminUsersPage'
import { AvailabilityPage } from './pages/AvailabilityPages'
import { CallupEditorPage } from './pages/CallupPages'
import { EmailsPage } from './pages/EmailPages'
import {
  ForgotPasswordPage,
  LoginPage,
  RegisterPage,
  ResetPasswordPage,
  VerifyEmailPage,
} from './pages/AuthPages'
import HomePage from './pages/HomePage'
import { CalendarPage, MatchDetailPage, MatchFormPage } from './pages/MatchPages'
import NotFoundPage from './pages/NotFoundPage'
import ProfilePage from './pages/ProfilePage'

export const routes = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      // Link ricevuti via email: accessibili anche da utenti già autenticati.
      { path: 'reset-password', element: <ResetPasswordPage /> },
      { path: 'verify-email', element: <VerifyEmailPage /> },
      {
        element: <RequireAnonymous />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
          { path: 'forgot-password', element: <ForgotPasswordPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          { path: 'profile', element: <ProfilePage /> },
          { path: 'calendar', element: <CalendarPage /> },
          { path: 'availability', element: <AvailabilityPage /> },
          { path: 'matches/:id', element: <MatchDetailPage /> },
        ],
      },
      {
        element: <RequireAuth role="COACH" />,
        children: [
          { path: 'matches/:id/callup', element: <CallupEditorPage /> },
          { path: 'emails', element: <EmailsPage /> },
        ],
      },
      {
        element: <RequireAuth role="ADMIN" />,
        children: [
          { path: 'admin/users', element: <AdminUsersPage /> },
          { path: 'matches/new', element: <MatchFormPage /> },
          { path: 'matches/:id/edit', element: <MatchFormPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
