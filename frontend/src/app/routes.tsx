import type { RouteObject } from 'react-router';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { GuestRoute } from './GuestRoute';
import { HomePage } from './HomePage';
import { Layout } from './Layout';
import { NotFoundPage } from './NotFoundPage';
import { ProtectedRoute } from './ProtectedRoute';

/**
 * Rutas de la SPA (en español). Las de cada funcionalidad se añaden como hijas de `protected`:
 * `/tablero` (JP-FE-06), onboarding (JP-FE-02), ofertas (JP-FE-03/04)…
 */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      {
        element: <GuestRoute />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'registro', element: <RegisterPage /> },
        ],
      },
      {
        id: 'protected',
        element: <ProtectedRoute />,
        children: [{ index: true, element: <HomePage /> }],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
