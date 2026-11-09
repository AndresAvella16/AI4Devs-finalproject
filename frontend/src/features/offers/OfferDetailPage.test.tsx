import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveSession } from '../../api/session';
import { API, authResponse, EXAMPLE_OFFER_ID, exampleOffer, failedOffer, problem } from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

function renderDetail(offerId = EXAMPLE_OFFER_ID) {
  saveSession(authResponse());
  return renderWithProviders({ path: `/ofertas/${offerId}` });
}

describe('OfferDetailPage — detalle básico de la oferta', () => {
  it('muestra puesto, empresa, ubicación, modalidad, salario y enlace a la oferta original', async () => {
    renderDetail();

    expect(await screen.findByRole('heading', { level: 1, name: 'Desarrollador/a Java Junior' })).toBeInTheDocument();
    const header = screen.getByTestId('offer-header');
    expect(header).toHaveTextContent('Empresa no indicada');
    expect(header).toHaveTextContent('Valencia');
    expect(header).toHaveTextContent('Híbrido');
    expect(screen.getByTestId('offer-salary')).toHaveTextContent(/24\.000.*28\.000/);
    const link = screen.getByRole('link', { name: /Ver la oferta original/ });
    expect(link).toHaveAttribute('href', 'https://empresa-ejemplo.com/empleo/java-junior');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('agrupa los requisitos en Obligatorios y Deseables, en el orden de la oferta', async () => {
    renderDetail();

    const mandatory = await screen.findByRole('region', { name: 'Obligatorios' });
    const optional = screen.getByRole('region', { name: 'Deseables' });
    expect(within(mandatory).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      expect.stringContaining('Al menos 1 año de experiencia con Java y Spring Boot'),
      expect.stringContaining('Conocimientos de SQL y PostgreSQL'),
      expect.stringContaining('Uso de Git'),
    ]);
    expect(within(optional).getAllByRole('listitem')).toHaveLength(3);
    expect(within(optional).getAllByRole('listitem')[2]).toHaveTextContent('Inglés B2');
    expect(within(optional).getAllByRole('listitem')[2]).toHaveTextContent('Idioma');
  });

  it('sin salario no muestra la línea de salario', async () => {
    server.use(http.get(`${API}/offers/:offerId`, () => HttpResponse.json(exampleOffer({ salary: null }))));
    renderDetail();

    await screen.findByRole('heading', { level: 1, name: 'Desarrollador/a Java Junior' });
    expect(screen.queryByTestId('offer-salary')).not.toBeInTheDocument();
  });

  it('con un solo extremo del salario lo indica como "desde"', async () => {
    server.use(
      http.get(`${API}/offers/:offerId`, () =>
        HttpResponse.json(exampleOffer({ salary: { min: 30000, max: null, currency: 'EUR' } })),
      ),
    );
    renderDetail();

    expect(await screen.findByTestId('offer-salary')).toHaveTextContent(/Desde 30\.000/);
  });

  it('con la extracción FALLIDA permite reintentarla y muestra el resultado', async () => {
    let retried = false;
    server.use(
      http.get(`${API}/offers/:offerId`, () => HttpResponse.json(retried ? exampleOffer() : failedOffer())),
      http.post(`${API}/offers/:offerId/extraction`, () => {
        retried = true;
        return HttpResponse.json(exampleOffer());
      }),
    );
    const { user } = renderDetail();

    const alert = await screen.findByTestId('extraction-failed');
    expect(alert).toHaveTextContent('No se han podido extraer los datos de la oferta');
    expect(screen.getByRole('heading', { level: 1, name: 'Oferta sin título' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Obligatorios' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reintentar extracción' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Desarrollador/a Java Junior' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Obligatorios' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar extracción' })).not.toBeInTheDocument();
  });

  it('con la extracción PENDIENTE también permite reintentarla', async () => {
    server.use(
      http.get(`${API}/offers/:offerId`, () => HttpResponse.json(failedOffer({ extractionStatus: 'PENDIENTE' }))),
    );
    renderDetail();

    expect(await screen.findByTestId('extraction-pending')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar extracción' })).toBeEnabled();
  });

  it('si el reintento falla muestra el error de la IA y se puede volver a intentar', async () => {
    server.use(
      http.get(`${API}/offers/:offerId`, () => HttpResponse.json(failedOffer())),
      http.post(`${API}/offers/:offerId/extraction`, () =>
        problem(
          502,
          'llm-invalid-response',
          'Respuesta de la IA no válida',
          'La IA ha devuelto una respuesta que no se ha podido procesar. Inténtalo de nuevo.',
        ),
      ),
    );
    const { user } = renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Reintentar extracción' }));

    const error = await screen.findByTestId('retry-extraction-error');
    expect(error).toHaveTextContent('Respuesta de la IA no válida');
    expect(screen.getByRole('button', { name: 'Reintentar extracción' })).toBeEnabled();
  });

  it('si otra pestaña ya completó la extracción (409) recarga la oferta', async () => {
    let completed = false;
    server.use(
      http.get(`${API}/offers/:offerId`, () => HttpResponse.json(completed ? exampleOffer() : failedOffer())),
      http.post(`${API}/offers/:offerId/extraction`, () => {
        completed = true;
        return problem(
          409,
          'offer-already-extracted',
          'La oferta ya tiene sus requisitos extraídos',
          'La oferta ya tiene sus requisitos extraídos.',
        );
      }),
    );
    const { user } = renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Reintentar extracción' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Desarrollador/a Java Junior' })).toBeInTheDocument();
  });

  it('una oferta que no existe (o es de otro usuario) muestra "Oferta no encontrada"', async () => {
    renderDetail('00000000-0000-4000-8000-000000000000');

    const notFound = await screen.findByTestId('offer-not-found');
    expect(notFound).toHaveTextContent('Oferta no encontrada');
    expect(within(notFound).getByRole('link', { name: 'Volver a tus ofertas' })).toHaveAttribute('href', '/');
  });
});
