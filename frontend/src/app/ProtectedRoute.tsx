import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../features/auth/useAuth';
import type { LoginRedirectState } from '../features/auth/redirect';

/**
 * Rutas que exigen sesión. Sin ella manda a /login recordando la ruta pedida, salvo si el usuario acaba de
 * cerrar sesión: así quien entre después no aterriza en una página de la sesión anterior.
 */
export function ProtectedRoute() {
  const { session, signOutReason } = useAuth();
  const location = useLocation();

  if (session === null) {
    const state: LoginRedirectState | null = signOutReason === 'logout' ? null : { from: location };
    return <Navigate to="/login" replace state={state} />;
  }
  return <Outlet />;
}
