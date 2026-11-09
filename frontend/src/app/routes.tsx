import type { RouteObject } from 'react-router';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { NewOfferPage } from '../features/offers/NewOfferPage';
import { OfferDetailPage } from '../features/offers/OfferDetailPage';
import { OffersDashboardPage } from '../features/offers/OffersDashboardPage';
import { GuestRoute } from './GuestRoute';
import { Layout } from './Layout';
import { NotFoundPage } from './NotFoundPage';
import { ProtectedRoute } from './ProtectedRoute';

/**
 * Rutas de la SPA (en español). Las de cada funcionalidad se añaden como hijas de `protected`:
 * `/` dashboard de ofertas, `/ofertas/nueva` y `/ofertas/:offerId` (JP-FE-03/04); `/tablero` (JP-FE-06), onboarding (JP-FE-02)…
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
        children: [
          { index: true, element: <OffersDashboardPage /> },
          { path: 'ofertas/nueva', element: <NewOfferPage /> },
          { path: 'ofertas/:offerId', element: <OfferDetailPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
