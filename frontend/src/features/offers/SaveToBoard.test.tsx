import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import type { CreateApplicationRequest } from '../../api/applications';
import { saveSession } from '../../api/session';
import {
  API,
  applicationAlreadyExists,
  authResponse,
  EXAMPLE_OFFER_ID,
  failedOffer,
  resetApplicationStore,
  seedApplication,
  seedApplications,
  storedApplications,
} from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

function renderDetail() {
  saveSession(authResponse());
  return renderWithProviders({ path: `/ofertas/${EXAMPLE_OFFER_ID}` });
}

/** Cuerpos de los `POST /applications` que llegan a la API. */
function recordCreates() {
  const bodies: unknown[] = [];
  server.events.on('request:start', async ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/v1/applications') {
      bodies.push(await request.clone().json());
    }
  });
  return bodies;
}

afterEach(() => server.events.removeAllListeners());

describe('OfferDetailPage — Guardar en el tablero', () => {
  it('guarda la oferta con POST /applications, avisa con un toast y cambia el botón por "Ver en el tablero"', async () => {
    const creates = recordCreates();
    const { user } = renderDetail();

    const button = await screen.findByRole('button', { name: 'Guardar en el tablero' });
    expect(screen.queryByRole('link', { name: 'Ver en el tablero' })).not.toBeInTheDocument();
    await user.click(button);

    const link = await screen.findByRole('link', { name: 'Ver en el tablero' });
    expect(link).toHaveAttribute('href', '/tablero');
    expect(screen.queryByRole('button', { name: 'Guardar en el tablero' })).not.toBeInTheDocument();
    expect(creates).toEqual([{ offerId: EXAMPLE_OFFER_ID } satisfies CreateApplicationRequest]);
    expect(within(await screen.findByTestId('toast')).getByText('Guardada en el tablero')).toBeInTheDocument();
    expect(storedApplications().filter((card) => card.offerId === EXAMPLE_OFFER_ID)).toHaveLength(1);
  });

  it('si la oferta ya está en el tablero, muestra directamente el enlace "Ver en el tablero"', async () => {
    resetApplicationStore([...seedApplications(), { ...seedApplication(2), id: 'cccccccc-0000-4000-8000-000000000001', offerId: EXAMPLE_OFFER_ID }]);
    renderDetail();

    expect(await screen.findByRole('link', { name: 'Ver en el tablero' })).toHaveAttribute('href', '/tablero');
    expect(screen.queryByRole('button', { name: 'Guardar en el tablero' })).not.toBeInTheDocument();
  });

  it('un 409 application-already-exists (p. ej. guardada desde otra pestaña) se trata como guardada', async () => {
    server.use(
      http.post(`${API}/applications`, () => {
        // Otra pestaña la guardó antes: ya está en el servidor.
        resetApplicationStore([...seedApplications(), { ...seedApplication(2), id: 'cccccccc-0000-4000-8000-000000000002', offerId: EXAMPLE_OFFER_ID }]);
        return applicationAlreadyExists();
      }),
    );
    const { user } = renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Guardar en el tablero' }));

    expect(await screen.findByRole('link', { name: 'Ver en el tablero' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar en el tablero' })).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(within(await screen.findByTestId('toast')).getByText('Esta oferta ya estaba en el tablero')).toBeInTheDocument();
  });

  it('otro error se muestra en un toast y el botón sigue disponible', async () => {
    server.use(
      http.post(`${API}/applications`, () =>
        HttpResponse.json(
          { type: 'https://jobpilot.dev/problems/internal-error', title: 'Error interno', status: 500, detail: 'Algo ha fallado.' },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const { user } = renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Guardar en el tablero' }));

    expect(await screen.findByTestId('toast')).toHaveTextContent('Algo ha fallado.');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Guardar en el tablero' })).toBeEnabled());
  });

  it('también se puede guardar una oferta cuya extracción ha fallado', async () => {
    server.use(http.get(`${API}/offers/:offerId`, () => HttpResponse.json(failedOffer())));
    renderDetail();

    expect(await screen.findByRole('button', { name: 'Guardar en el tablero' })).toBeInTheDocument();
  });
});
