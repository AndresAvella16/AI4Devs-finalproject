import type { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { AppProviders } from '../app/AppProviders';
import { createQueryClient } from '../app/queryClient';
import { routes as appRoutes } from '../app/routes';

interface Options {
  /** Ruta inicial (por defecto `/`). */
  path?: string;
  /** Rutas a montar; por defecto, las de la aplicación. */
  routes?: RouteObject[];
  queryClient?: QueryClient;
}

/** Monta la app (o unas rutas concretas) con los mismos providers que en producción y un router en memoria. */
export function renderWithProviders({ path = '/', routes = appRoutes, queryClient = createQueryClient() }: Options = {}) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const user = userEvent.setup();
  const view = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...view, router, user, queryClient };
}
