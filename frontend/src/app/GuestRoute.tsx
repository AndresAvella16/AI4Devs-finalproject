import { Navigate, Outlet, useLocation } from 'react-router';
import { redirectTarget } from '../features/auth/redirect';
import { useAuth } from '../features/auth/useAuth';

/**
 * Login y registro. En cuanto hay sesión (porque ya la había o porque se acaba de iniciar) redirige a la ruta
 * que se pidió antes de entrar, o al inicio. Es el único sitio que hace esa redirección.
 */
export function GuestRoute() {
  const { session } = useAuth();
  const location = useLocation();
  return session === null ? <Outlet /> : <Navigate to={redirectTarget(location.state)} replace />;
}
