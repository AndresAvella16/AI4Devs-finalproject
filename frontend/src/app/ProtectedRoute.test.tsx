import { useQuery } from '@tanstack/react-query';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { apiFetch } from '../api/client';
import { loadSession, saveSession } from '../api/session';
import { API, authResponse, DEMO_EMAIL, DEMO_PASSWORD, problem } from '../test/handlers';
import { renderWithProviders } from '../test/renderWithProviders';
import { server } from '../test/server';
import { routes } from './routes';

function Private() {
  const query = useQuery({ queryKey: ['ping'], queryFn: () => apiFetch<{ ok: boolean }>('/ping') });
  return <p>Zona privada {query.data?.ok ? 'cargada' : ''}</p>;
}

/** Las rutas de la app más una ruta protegida de prueba que llama a la API. */
const routesWithPrivate: RouteObject[] = routes.map((root) => ({
  ...root,
  children: [
    ...(root.children ?? []).map((child) =>
      child.id === 'protected' ? { ...child, children: [...(child.children ?? []), { path: 'privada', element: <Private /> }] } : child,
    ),
  ],
})) as RouteObject[];

describe('rutas protegidas y sesión', () => {
  it('sin sesión, / redirige a /login', async () => {
    const { router } = renderWithProviders({ path: '/' });

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('con sesión, / muestra el inicio con el nombre del usuario', async () => {
    saveSession(authResponse());
    renderWithProviders({ path: '/' });

    expect(await screen.findByTestId('current-user-name')).toHaveTextContent('Lucía Demo');
    expect(screen.getByTestId('home-greeting')).toHaveTextContent('Hola, Lucía Demo');
  });

  it('con el token caducado, se trata como sin sesión', async () => {
    saveSession(authResponse(), Date.now() - 2 * 3600 * 1000);
    const { router } = renderWithProviders({ path: '/' });

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('tras iniciar sesión vuelve a la ruta protegida que se pidió', async () => {
    server.use(http.get(`${API}/ping`, () => HttpResponse.json({ ok: true })));
    const { user, router } = renderWithProviders({ path: '/privada', routes: routesWithPrivate });

    await screen.findByRole('heading', { name: 'Iniciar sesión' });
    await user.type(screen.getByLabelText('Email'), DEMO_EMAIL);
    await user.type(screen.getByLabelText('Contraseña'), DEMO_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));

    expect(await screen.findByText('Zona privada cargada')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/privada');
  });

  it('"Cerrar sesión" borra la sesión, vacía la caché y vuelve a /login', async () => {
    saveSession(authResponse());
    const { user, router, queryClient } = renderWithProviders({ path: '/' });
    queryClient.setQueryData(['algo'], { privado: true });

    await user.click(await screen.findByTestId('logout-button'));

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.state).toBeNull();
    expect(loadSession()).toBeNull();
    expect(queryClient.getQueryData(['algo'])).toBeUndefined();
    expect(screen.queryByTestId('current-user-name')).not.toBeInTheDocument();
  });

  it('un 401 de la API en una página protegida cierra la sesión y lleva a /login', async () => {
    saveSession(authResponse());
    server.use(http.get(`${API}/ping`, () => problem(401, 'unauthorized', 'No autenticado', 'Token caducado.')));
    const { router } = renderWithProviders({ path: '/privada', routes: routesWithPrivate });

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(loadSession()).toBeNull();
    expect(screen.getByTestId('session-expired')).toHaveTextContent('Tu sesión ha caducado');
  });

  it('con sesión, /login y /registro redirigen al inicio', async () => {
    saveSession(authResponse());
    const { router } = renderWithProviders({ path: '/registro' });

    expect(await screen.findByTestId('home-greeting')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it('una ruta inexistente muestra la página 404', async () => {
    renderWithProviders({ path: '/no-existe' });

    expect(await screen.findByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument();
  });
});
