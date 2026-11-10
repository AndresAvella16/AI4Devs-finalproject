import type { RouteObject } from 'react-router';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { OffersDashboardPage } from '../features/offers/OffersDashboardPage';
import { ProfilePlaceholderPage } from '../features/profile/ProfilePlaceholderPage';
import { GuestRoute } from './GuestRoute';
import { Layout } from './Layout';
import { NotFoundPage } from './NotFoundPage';
import { PageLoading } from './PageLoading';
import { ProtectedRoute } from './ProtectedRoute';

/**
 * Rutas de la SPA (en español). Las de cada funcionalidad se añaden como hijas de `protected`:
 * `/` dashboard de ofertas, `/ofertas/nueva` y `/ofertas/:offerId` (JP-FE-03/04); `/perfil` (provisional hasta el
 * onboarding de JP-FE-02); `/tablero` (JP-FE-06).
 *
 * Las páginas grandes se cargan con `lazy` de React Router (un chunk por página) para que el bundle inicial no pase de 500 kB:
 * el tablero (dnd-kit), el detalle de oferta (panel de análisis) y la nueva oferta.
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
          {
            path: 'ofertas/nueva',
            HydrateFallback: PageLoading,
            lazy: () => import('../features/offers/NewOfferPage').then(({ NewOfferPage }) => ({ Component: NewOfferPage })),
          },
          {
            path: 'ofertas/:offerId',
            HydrateFallback: PageLoading,
            lazy: () =>
              import('../features/offers/OfferDetailPage').then(({ OfferDetailPage }) => ({ Component: OfferDetailPage })),
          },
          { path: 'perfil', element: <ProfilePlaceholderPage /> },
          {
            path: 'tablero',
            HydrateFallback: PageLoading,
            lazy: () => import('../features/tracker/BoardPage').then(({ BoardPage }) => ({ Component: BoardPage })),
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
