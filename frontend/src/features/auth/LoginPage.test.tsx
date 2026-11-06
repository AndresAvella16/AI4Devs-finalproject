import { screen } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { loadSession } from '../../api/session';
import { API, DEMO_EMAIL, DEMO_PASSWORD } from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

describe('LoginPage', () => {
  it('valida en cliente y no llama a la API si el formulario no es válido', async () => {
    const onRequest = vi.fn();
    server.events.on('request:start', onRequest);
    const { user } = renderWithProviders({ path: '/login' });

    await user.type(screen.getByLabelText('Email'), 'no-es-un-email');
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));

    expect(await screen.findByText('Introduce un email válido')).toBeInTheDocument();
    expect(screen.getByText('Introduce tu contraseña')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(onRequest).not.toHaveBeenCalled();
    server.events.removeAllListeners();
  });

  it('con credenciales correctas guarda la sesión y lleva al inicio con el nombre del usuario', async () => {
    const { user, router } = renderWithProviders({ path: '/login' });

    await user.type(screen.getByTestId('login-email'), DEMO_EMAIL);
    await user.type(screen.getByTestId('login-password'), DEMO_PASSWORD);
    await user.click(screen.getByTestId('login-submit'));

    expect(await screen.findByTestId('current-user-name')).toHaveTextContent('Lucía Demo');
    expect(router.state.location.pathname).toBe('/');
    expect(loadSession()?.accessToken).toBe('test.jwt.token');
  });

  it('con un 401 muestra "Credenciales incorrectas" y se queda en /login', async () => {
    const { user, router } = renderWithProviders({ path: '/login' });

    await user.type(screen.getByLabelText('Email'), DEMO_EMAIL);
    await user.type(screen.getByLabelText('Contraseña'), 'Incorrecta1');
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));

    const alert = await screen.findByTestId('login-error');
    expect(alert).toHaveTextContent('Credenciales incorrectas');
    expect(alert).toHaveTextContent('El email o la contraseña no son correctos.');
    expect(alert).toHaveAttribute('role', 'alert');
    expect(router.state.location.pathname).toBe('/login');
    expect(loadSession()).toBeNull();
  });

  it('deshabilita el botón mientras se envía', async () => {
    let release: () => void = () => {};
    server.use(
      http.post(`${API}/auth/login`, () => new Promise<Response>((resolve) => {
        release = () => resolve(Response.json({}, { status: 500 }));
      })),
    );
    const { user } = renderWithProviders({ path: '/login' });

    await user.type(screen.getByLabelText('Email'), DEMO_EMAIL);
    await user.type(screen.getByLabelText('Contraseña'), DEMO_PASSWORD);
    await user.click(screen.getByTestId('login-submit'));

    expect(await screen.findByRole('button', { name: 'Entrando…' })).toBeDisabled();
    release();
    expect(await screen.findByTestId('login-error')).toBeInTheDocument();
  });

  it('enlaza con la página de registro', async () => {
    const { user, router } = renderWithProviders({ path: '/login' });

    await user.click(screen.getByRole('link', { name: 'Crea una cuenta' }));

    expect(router.state.location.pathname).toBe('/registro');
  });
});
