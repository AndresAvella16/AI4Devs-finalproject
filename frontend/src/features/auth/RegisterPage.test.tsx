import { screen } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { loadSession } from '../../api/session';
import { API, problem } from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

async function fillAndSubmit(user: ReturnType<typeof renderWithProviders>['user'], password = 'Secreto123') {
  await user.type(screen.getByLabelText('Nombre completo'), 'Ana Pérez');
  await user.type(screen.getByLabelText('Email'), 'Ana@Example.com');
  await user.type(screen.getByLabelText('Contraseña'), password);
  await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));
}

describe('RegisterPage', () => {
  it('con datos válidos crea la cuenta, inicia sesión y lleva al inicio', async () => {
    const { user, router } = renderWithProviders({ path: '/registro' });

    await fillAndSubmit(user);

    expect(await screen.findByTestId('current-user-name')).toHaveTextContent('Ana Pérez');
    expect(router.state.location.pathname).toBe('/');
    expect(loadSession()?.user.email).toBe('ana@example.com');
  });

  it('valida la política de contraseñas en cliente', async () => {
    const { user } = renderWithProviders({ path: '/registro' });

    await fillAndSubmit(user, 'abcdefgh');

    expect(await screen.findByText('La contraseña debe incluir al menos una letra y un número')).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('aria-invalid', 'true');
  });

  it('con un 409 muestra el error en el campo email', async () => {
    server.use(
      http.post(`${API}/auth/register`, () =>
        problem(409, 'email-already-registered', 'Email ya registrado', 'Ya existe una cuenta con ese email.'),
      ),
    );
    const { user, router } = renderWithProviders({ path: '/registro' });

    await fillAndSubmit(user);

    const email = screen.getByLabelText('Email');
    expect(await screen.findByText('Ya existe una cuenta con ese email.')).toBeInTheDocument();
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(email).toHaveAccessibleDescription('Ya existe una cuenta con ese email.');
    expect(router.state.location.pathname).toBe('/registro');
  });

  it('con un 400 reparte los errors[] del backend por campo', async () => {
    server.use(
      http.post(`${API}/auth/register`, () =>
        problem(400, 'validation-error', 'Datos de entrada no válidos', 'Revisa los campos marcados.', {
          errors: [
            { field: 'fullName', message: 'no debe estar vacío' },
            { field: 'password', message: 'contraseña no válida' },
          ],
        }),
      ),
    );
    const { user } = renderWithProviders({ path: '/registro' });

    await fillAndSubmit(user);

    expect(await screen.findByText('no debe estar vacío')).toBeInTheDocument();
    expect(screen.getByText('contraseña no válida')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre completo')).toHaveAttribute('aria-invalid', 'true');
  });

  it('con un error inesperado muestra un aviso general', async () => {
    server.use(
      http.post(`${API}/auth/register`, () => problem(500, 'internal-error', 'Error interno', 'Ha ocurrido un error.')),
    );
    const { user } = renderWithProviders({ path: '/registro' });

    await fillAndSubmit(user);

    expect(await screen.findByTestId('register-error')).toHaveTextContent('Error interno');
  });
});
