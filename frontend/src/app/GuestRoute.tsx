import { useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { redirectTarget } from '../features/auth/redirect';
import { useAuth } from '../features/auth/useAuth';

/** Tras crear la cuenta se va a crear el perfil (flujo de la sección 1.3): un usuario nuevo nunca tiene perfil. */
export const AFTER_REGISTER_PATH = '/perfil';

/**
 * Login y registro. En cuanto hay sesión (porque ya la había o porque se acaba de iniciar) redirige: si se acaba de
 * crear la cuenta, al onboarding del perfil; si no, a la ruta que se pidió antes de entrar, o al inicio. Es el único
 * sitio que hace esa redirección.
 */
export function GuestRoute() {
  const { session } = useAuth();
  const location = useLocation();
  // Solo cuenta como registro si se ha entrado sin sesión: con sesión, /registro lleva al inicio como /login.
  const [enteredAsGuest] = useState(session === null);
  if (session === null) return <Outlet />;
  const justRegistered = enteredAsGuest && location.pathname === '/registro';
  const target = justRegistered ? AFTER_REGISTER_PATH : redirectTarget(location.state);
  return <Navigate to={target} replace />;
}
