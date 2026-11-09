import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { OfferSummary } from '../../api/offers';
import { saveSession } from '../../api/session';
import { createQueryClient } from '../../app/queryClient';
import { API, authResponse, problem, seedOffers } from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

function renderDashboard() {
  saveSession(authResponse());
  // Sin reintentos automáticos, para comprobar el estado de error sin esperas.
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({ queries: { ...queryClient.getDefaultOptions().queries, retry: false } });
  return renderWithProviders({ path: '/', queryClient });
}

function itemFor(title: string): HTMLElement {
  const item = screen.getAllByTestId('offer-item').find((element) => element.textContent?.includes(title));
  if (!item) throw new Error(`No hay ninguna oferta con el título ${title}`);
  return item;
}

describe('OffersDashboardPage — dashboard de ofertas', () => {
  it('lista las ofertas en el orden de la API con empresa, badge de puntuación y candidatura', async () => {
    renderDashboard();

    expect(screen.getByRole('heading', { level: 1, name: 'Tus ofertas' })).toBeInTheDocument();
    const list = await screen.findByTestId('offer-list');
    const items = within(list).getAllByTestId('offer-item');
    expect(items).toHaveLength(3);
    expect(items.map((item) => item.querySelector('h2')?.textContent)).toEqual([
      'Desarrollador/a Frontend Senior (Angular)',
      'Desarrollador/a Full Stack',
      'Desarrollador/a Java Junior',
    ]);

    const java = itemFor('Desarrollador/a Java Junior');
    expect(java).toHaveTextContent('Innova Software Ficticio');
    expect(within(java).getByTestId('match-score-badge')).toHaveAttribute('data-tone', 'alto');
    expect(within(java).getByTestId('application-status')).toHaveTextContent('Entrevista');

    expect(within(itemFor('Desarrollador/a Full Stack')).getByTestId('match-score-badge')).toHaveAttribute(
      'data-tone',
      'medio',
    );
  });

  it('señala la oferta con requisito bloqueante', async () => {
    renderDashboard();
    await screen.findByTestId('offer-list');

    const angular = itemFor('Desarrollador/a Frontend Senior (Angular)');
    expect(within(angular).getByTestId('match-score-badge')).toHaveAttribute('data-tone', 'bajo');
    expect(within(angular).getByTestId('blocking-gap')).toHaveTextContent('Requisito bloqueante');
    expect(within(itemFor('Desarrollador/a Java Junior')).queryByTestId('blocking-gap')).not.toBeInTheDocument();
  });

  it('cada oferta enlaza a su detalle', async () => {
    renderDashboard();
    await screen.findByTestId('offer-list');

    expect(within(itemFor('Desarrollador/a Java Junior')).getByRole('link')).toHaveAttribute(
      'href',
      '/ofertas/55555555-0000-4000-8000-000000000001',
    );
  });

  it('identifica las ofertas cuya extracción ha fallado o está pendiente', async () => {
    const failed: OfferSummary = {
      id: 'f0000000-0000-4000-8000-000000000001',
      title: null,
      company: null,
      createdAt: '2026-11-08T09:00:00Z',
      extractionStatus: 'FALLIDA',
      latestScore: null,
      hasBlockingGap: null,
      applicationStatus: null,
    };
    const pending: OfferSummary = { ...failed, id: 'f0000000-0000-4000-8000-000000000002', extractionStatus: 'PENDIENTE' };
    server.use(http.get(`${API}/offers`, () => HttpResponse.json([failed, pending])));
    renderDashboard();

    const items = within(await screen.findByTestId('offer-list')).getAllByTestId('offer-item');
    expect(items).toHaveLength(2);
    const [failedItem, pendingItem] = items as [HTMLElement, HTMLElement];
    expect(failedItem).toHaveTextContent('Oferta sin título');
    expect(within(failedItem).getByTestId('extraction-status')).toHaveTextContent('Extracción fallida');
    expect(within(failedItem).getByTestId('match-score-badge')).toHaveTextContent('Sin analizar');
    expect(within(pendingItem).getByTestId('extraction-status')).toHaveTextContent('Extracción pendiente');
  });

  it('sin ofertas muestra el estado vacío con enlace al formulario', async () => {
    server.use(http.get(`${API}/offers`, () => HttpResponse.json([])));
    renderDashboard();

    const empty = await screen.findByTestId('offers-empty');
    expect(empty).toHaveTextContent('Aún no has importado ninguna oferta');
    expect(within(empty).getByRole('link', { name: 'Importar tu primera oferta' })).toHaveAttribute(
      'href',
      '/ofertas/nueva',
    );
  });

  it('si la API falla muestra un error y "Reintentar" vuelve a pedir las ofertas', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/offers`, () => {
        calls += 1;
        return calls === 1
          ? problem(500, 'internal-error', 'Error interno', 'Se ha producido un error inesperado.')
          : HttpResponse.json(seedOffers);
      }),
    );
    const { user } = renderDashboard();

    const error = await screen.findByTestId('offers-error');
    expect(error).toHaveTextContent('No se han podido cargar tus ofertas');
    await user.click(within(error).getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByTestId('offer-list')).toBeInTheDocument();
    expect(calls).toBe(2);
  });

  it('mientras carga se anuncia el estado de carga', async () => {
    renderDashboard();

    expect(screen.getByTestId('offers-loading')).toHaveAttribute('aria-busy', 'true');
    await screen.findByTestId('offer-list');
  });

  it('el botón "Nueva oferta" lleva al formulario', async () => {
    const { user, router } = renderDashboard();
    await screen.findByTestId('offer-list');

    await user.click(screen.getByTestId('new-offer-button'));

    expect(await screen.findByRole('heading', { name: 'Nueva oferta' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/ofertas/nueva');
  });
});
