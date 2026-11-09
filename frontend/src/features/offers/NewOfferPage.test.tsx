import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { CreateOfferRequest, OfferSummary } from '../../api/offers';
import { saveSession } from '../../api/session';
import {
  API,
  authResponse,
  EXAMPLE_OFFER_ID,
  EXAMPLE_OFFER_TEXT,
  exampleOffer,
  problem,
  seedOffers,
} from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

function renderNewOffer() {
  saveSession(authResponse());
  return renderWithProviders({ path: '/ofertas/nueva' });
}

async function pasteInto(label: string, text: string, user: ReturnType<typeof renderNewOffer>['user']) {
  const field = screen.getByLabelText(label);
  await user.click(field);
  await user.paste(text);
}

describe('NewOfferPage — formulario de nueva oferta', () => {
  it('con el texto vacío, el botón está deshabilitado y se ve cuántos caracteres faltan', async () => {
    renderNewOffer();

    expect(await screen.findByRole('heading', { name: 'Nueva oferta' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Analizar oferta' })).toBeDisabled();
    expect(screen.getByTestId('offer-char-counter')).toHaveTextContent('Faltan 200 caracteres');
  });

  it('con menos de 200 caracteres sigue deshabilitado y el contador se actualiza', async () => {
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', 'a'.repeat(199), user);

    expect(screen.getByTestId('offer-char-counter')).toHaveTextContent('Falta 1 carácter');
    expect(screen.getByRole('button', { name: 'Analizar oferta' })).toBeDisabled();
  });

  it('con 200 caracteres se habilita (los emojis cuentan como 1)', async () => {
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', '😀'.repeat(200), user);

    expect(screen.getByTestId('offer-char-counter')).toHaveTextContent('200 / 20.000 caracteres');
    expect(screen.getByRole('button', { name: 'Analizar oferta' })).toBeEnabled();
  });

  it('con más de 20.000 caracteres se deshabilita y se ve cuántos sobran', async () => {
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', 'a'.repeat(20_002), user);

    expect(screen.getByTestId('offer-char-counter')).toHaveTextContent('Sobran 2 caracteres');
    expect(screen.getByRole('button', { name: 'Analizar oferta' })).toBeDisabled();
  });

  it('envía la oferta, abre su detalle y, al volver, aparece en el dashboard', async () => {
    let sent: CreateOfferRequest | null = null;
    const offers: OfferSummary[] = [...seedOffers];
    server.use(
      http.post(`${API}/offers`, async ({ request }) => {
        sent = (await request.json()) as CreateOfferRequest;
        const created = exampleOffer();
        offers.unshift({
          id: created.id,
          title: created.title,
          company: created.company,
          createdAt: created.createdAt,
          extractionStatus: created.extractionStatus,
          latestScore: null,
          hasBlockingGap: null,
          applicationStatus: null,
        });
        return HttpResponse.json(created, { status: 201 });
      }),
      http.get(`${API}/offers`, () => HttpResponse.json(offers)),
    );
    const { user, router } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', EXAMPLE_OFFER_TEXT, user);
    await pasteInto('URL de la oferta (opcional)', 'https://empresa-ejemplo.com/empleo/java-junior', user);
    await user.click(screen.getByRole('button', { name: 'Analizar oferta' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Desarrollador/a Java Junior' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/ofertas/${EXAMPLE_OFFER_ID}`);
    expect(sent).toEqual({ rawText: EXAMPLE_OFFER_TEXT, sourceUrl: 'https://empresa-ejemplo.com/empleo/java-junior' });

    await user.click(screen.getByRole('link', { name: 'Volver a tus ofertas' }));
    const list = await screen.findByTestId('offer-list');
    expect(within(list).getAllByTestId('offer-item')).toHaveLength(4);
    expect(within(list).getAllByTestId('offer-item')[0]).toHaveTextContent('Desarrollador/a Java Junior');
  });

  it('sin URL envía sourceUrl null', async () => {
    let sent: CreateOfferRequest | null = null;
    server.use(
      http.post(`${API}/offers`, async ({ request }) => {
        sent = (await request.json()) as CreateOfferRequest;
        return HttpResponse.json(exampleOffer({ sourceUrl: null }), { status: 201 });
      }),
    );
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', EXAMPLE_OFFER_TEXT, user);
    await user.click(screen.getByRole('button', { name: 'Analizar oferta' }));

    await screen.findByRole('heading', { level: 1, name: 'Desarrollador/a Java Junior' });
    expect(sent).toEqual({ rawText: EXAMPLE_OFFER_TEXT, sourceUrl: null });
  });

  it('una URL que no es http(s) se marca en el campo y no se envía nada', async () => {
    let requests = 0;
    server.use(
      http.post(`${API}/offers`, () => {
        requests += 1;
        return HttpResponse.json(exampleOffer(), { status: 201 });
      }),
    );
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', EXAMPLE_OFFER_TEXT, user);
    await pasteInto('URL de la oferta (opcional)', 'javascript:alert(1)', user);
    await user.click(screen.getByRole('button', { name: 'Analizar oferta' }));

    const urlField = screen.getByLabelText('URL de la oferta (opcional)');
    expect(urlField).toHaveAttribute('aria-invalid', 'true');
    expect(urlField).toHaveAccessibleDescription('Introduce una URL que empiece por http:// o https://');
    expect(requests).toBe(0);
  });

  it('al salir del campo, una URL no válida se marca aunque el texto aún sea corto (botón deshabilitado)', async () => {
    let requests = 0;
    server.use(
      http.post(`${API}/offers`, () => {
        requests += 1;
        return HttpResponse.json(exampleOffer(), { status: 201 });
      }),
    );
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('URL de la oferta (opcional)', 'javascript:alert(1)', user);
    const urlField = screen.getByLabelText('URL de la oferta (opcional)');
    expect(urlField).not.toHaveAttribute('aria-invalid');

    await user.tab();

    expect(await screen.findByText('Introduce una URL que empiece por http:// o https://')).toBeInTheDocument();
    expect(urlField).toHaveAttribute('aria-invalid', 'true');
    expect(urlField).toHaveAccessibleDescription('Introduce una URL que empiece por http:// o https://');
    expect(screen.getByRole('button', { name: 'Analizar oferta' })).toBeDisabled();
    expect(screen.getByLabelText('Texto de la oferta')).not.toHaveAttribute('aria-invalid');
    expect(requests).toBe(0);
  });

  it('el error de la URL desaparece al corregirla', async () => {
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('URL de la oferta (opcional)', 'javascript:alert(1)', user);
    await user.tab();
    const urlField = screen.getByLabelText('URL de la oferta (opcional)');
    expect(urlField).toHaveAttribute('aria-invalid', 'true');

    await user.clear(urlField);
    await user.type(urlField, 'https://ejemplo.com');

    expect(urlField).not.toHaveAttribute('aria-invalid');
    expect(urlField).toHaveAccessibleDescription('Solo se guarda como referencia: no se descarga.');
    expect(screen.queryByText('Introduce una URL que empiece por http:// o https://')).not.toBeInTheDocument();
  });

  it('salir del campo de URL vacío no muestra ningún error', async () => {
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await user.click(screen.getByLabelText('URL de la oferta (opcional)'));
    await user.tab();

    expect(screen.getByLabelText('URL de la oferta (opcional)')).not.toHaveAttribute('aria-invalid');
  });

  it('un 400 del backend se muestra en su campo', async () => {
    server.use(
      http.post(`${API}/offers`, () =>
        problem(400, 'validation-error', 'Datos de entrada no válidos', 'Hay campos con errores.', {
          errors: [{ field: 'rawText', message: 'debe tener entre 200 y 20.000 caracteres' }],
        }),
      ),
    );
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', EXAMPLE_OFFER_TEXT, user);
    await user.click(screen.getByRole('button', { name: 'Analizar oferta' }));

    const textField = screen.getByLabelText('Texto de la oferta');
    expect(await screen.findByText('debe tener entre 200 y 20.000 caracteres')).toBeInTheDocument();
    expect(textField).toHaveAttribute('aria-invalid', 'true');
  });

  it('un 429 muestra el aviso de límite de peticiones con el detalle del backend', async () => {
    server.use(
      http.post(`${API}/offers`, () =>
        problem(
          429,
          'rate-limit-exceeded',
          'Demasiadas peticiones',
          'Has superado el límite de peticiones a la IA. Vuelve a intentarlo en 42 segundos.',
        ),
      ),
    );
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', EXAMPLE_OFFER_TEXT, user);
    await user.click(screen.getByRole('button', { name: 'Analizar oferta' }));

    const alert = await screen.findByTestId('offer-create-error');
    expect(alert).toHaveTextContent('Demasiadas peticiones');
    expect(alert).toHaveTextContent('Vuelve a intentarlo en 42 segundos.');
    expect(screen.getByLabelText('Texto de la oferta')).toHaveValue(EXAMPLE_OFFER_TEXT);
  });

  it('mientras se analiza, el botón queda deshabilitado y se indica el progreso', async () => {
    let release: () => void = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.post(`${API}/offers`, async () => {
        await pending;
        return HttpResponse.json(exampleOffer(), { status: 201 });
      }),
    );
    const { user } = renderNewOffer();
    await screen.findByRole('heading', { name: 'Nueva oferta' });

    await pasteInto('Texto de la oferta', EXAMPLE_OFFER_TEXT, user);
    await user.click(screen.getByRole('button', { name: 'Analizar oferta' }));

    expect(await screen.findByRole('button', { name: 'Analizando oferta…' })).toBeDisabled();
    release();
    expect(await screen.findByRole('heading', { level: 1, name: 'Desarrollador/a Java Junior' })).toBeInTheDocument();
  });
});
