import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { saveSession } from '../../api/session';
import {
  API,
  authResponse,
  blockingAnalysis,
  EXAMPLE_OFFER_ID,
  exampleAnalysis,
  exampleOffer,
  failedOffer,
  problem,
} from '../../test/handlers';
import { createQueryClient } from '../../app/queryClient';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

function renderDetail(queryClient = createQueryClient()) {
  saveSession(authResponse());
  return renderWithProviders({ path: `/ofertas/${EXAMPLE_OFFER_ID}`, queryClient });
}

function withLatestAnalysis(analysis = exampleAnalysis()) {
  server.use(http.get(`${API}/offers/:offerId/analysis`, () => HttpResponse.json(analysis)));
}

async function findPanel() {
  return screen.findByRole('region', { name: 'Análisis de encaje' });
}

describe('AnalysisPanel — análisis de encaje en el detalle de la oferta (HU-03)', () => {
  it('sin análisis previo invita a calcular el encaje', async () => {
    renderDetail();

    const panel = await findPanel();
    expect(await within(panel).findByTestId('analysis-empty')).toHaveTextContent('Aún no has calculado tu encaje');
    expect(within(panel).getByRole('button', { name: 'Calcular encaje' })).toBeEnabled();
    expect(within(panel).queryByTestId('analysis-score')).not.toBeInTheDocument();
  });

  it('al pulsar "Calcular encaje" muestra la puntuación, cada requisito con su evidencia y las sugerencias', async () => {
    let posts = 0;
    server.use(
      http.post(`${API}/offers/:offerId/analysis`, async () => {
        posts += 1;
        await delay(50);
        return HttpResponse.json(exampleAnalysis(), { status: 201 });
      }),
    );
    const { user } = renderDetail();
    const panel = await findPanel();

    await user.click(await within(panel).findByRole('button', { name: 'Calcular encaje' }));

    // Mientras espera a la IA: indicador accesible y botón deshabilitado (no se lanzan dos cálculos).
    expect(within(panel).getByTestId('analysis-calculating')).toHaveTextContent('Calculando tu encaje…');
    expect(within(panel).getByRole('button', { name: /Calculando/ })).toBeDisabled();

    const score = await within(panel).findByTestId('analysis-score');
    expect(posts).toBe(1);
    expect(score).toHaveTextContent('85');
    expect(score).toHaveAccessibleName('Encaje: 85 de 100');
    expect(score).toHaveAttribute('data-tone', 'alto');
    expect(within(panel).getByTestId('analysis-summary')).toHaveTextContent(
      'Cumples todos los requisitos obligatorios. Tu principal margen de mejora está en Docker.',
    );

    const rows = within(panel).getAllByTestId('requirement-match-row');
    expect(rows.map((row) => row.getAttribute('data-result'))).toEqual([
      'CUMPLE',
      'CUMPLE',
      'CUMPLE',
      'PARCIAL',
      'NO_CUMPLE',
      'CUMPLE',
    ]);
    expect(rows[0]).toHaveTextContent('Al menos 1 año de experiencia con Java y Spring Boot');
    expect(rows[0]).toHaveTextContent('Obligatorio');
    expect(rows[0]).toHaveTextContent('Cumple');
    expect(rows[0]).toHaveTextContent('Prácticas de 12 meses como desarrollador Java (Spring Boot)');
    expect(rows[3]).toHaveTextContent('Parcial');
    expect(rows[3]).toHaveTextContent('Deseable');
    expect(rows[4]).toHaveTextContent('No cumple');
    expect(rows[4]).toHaveTextContent('Sin evidencia en tu perfil');

    const suggestions = within(within(panel).getByTestId('analysis-suggestions')).getAllByRole('listitem');
    expect(suggestions).toHaveLength(3);
    expect(suggestions[0]).toHaveTextContent('Pon tu experiencia con Spring Boot en la primera línea del resumen.');

    // Sin obligatorios incumplidos no hay aviso ni filas bloqueantes.
    expect(within(panel).queryByTestId('blocking-gap-alert')).not.toBeInTheDocument();
    expect(rows.filter((row) => row.getAttribute('data-blocking') === 'true')).toHaveLength(0);
  });

  it('si la oferta ya tiene análisis lo muestra al abrirla, con la opción de volver a calcular', async () => {
    withLatestAnalysis(exampleAnalysis({ score: 68 }));
    renderDetail();
    const panel = await findPanel();

    const score = await within(panel).findByTestId('analysis-score');
    expect(score).toHaveTextContent('68');
    expect(score).toHaveAttribute('data-tone', 'medio');
    expect(within(panel).queryByTestId('analysis-empty')).not.toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Volver a calcular' })).toHaveAccessibleDescription(
      /consume una consulta de IA/,
    );
  });

  it('volver a calcular sustituye el análisis mostrado por el nuevo', async () => {
    withLatestAnalysis(blockingAnalysis());
    const { user } = renderDetail();
    const panel = await findPanel();
    expect(await within(panel).findByTestId('analysis-score')).toHaveTextContent('42');

    await user.click(within(panel).getByRole('button', { name: 'Volver a calcular' }));

    await waitFor(() => expect(within(panel).getByTestId('analysis-score')).toHaveTextContent('85'));
    expect(within(panel).queryByTestId('blocking-gap-alert')).not.toBeInTheDocument();
  });

  it('con requisitos obligatorios incumplidos avisa y señala esos requisitos como bloqueantes', async () => {
    withLatestAnalysis(blockingAnalysis());
    renderDetail();
    const panel = await findPanel();

    const alert = await within(panel).findByTestId('blocking-gap-alert');
    expect(alert).toHaveTextContent('Hay requisitos obligatorios que no cumples');
    expect(alert).toHaveTextContent('Conocimientos de SQL y PostgreSQL');
    expect(alert).toHaveTextContent('Uso de Git');
    expect(alert).not.toHaveTextContent('Docker'); // deseable NO_CUMPLE: no es bloqueante

    expect(within(panel).getByTestId('analysis-score')).toHaveAttribute('data-tone', 'bajo');
    const blocking = within(panel)
      .getAllByTestId('requirement-match-row')
      .filter((row) => row.getAttribute('data-blocking') === 'true');
    expect(blocking.map((row) => row.textContent)).toEqual([
      expect.stringContaining('Conocimientos de SQL y PostgreSQL'),
      expect.stringContaining('Uso de Git'),
    ]);
    blocking.forEach((row) => expect(row).toHaveTextContent('Bloqueante'));
  });

  it('sin perfil (409 profile-required) lleva a crear el perfil', async () => {
    server.use(
      http.post(`${API}/offers/:offerId/analysis`, () =>
        problem(409, 'profile-required', 'Necesitas crear tu perfil', 'Crea tu perfil antes de calcular el encaje.'),
      ),
    );
    const { user, router } = renderDetail();
    const panel = await findPanel();

    await user.click(await within(panel).findByRole('button', { name: 'Calcular encaje' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Crea tu perfil' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/perfil');
    const placeholder = screen.getByTestId('profile-placeholder');
    expect(placeholder).toHaveTextContent('demo@jobpilot.dev');
    expect(within(placeholder).getByRole('link', { name: 'Volver a tus ofertas' })).toHaveAttribute('href', '/');
  });

  it('si la IA no responde a tiempo (504) avisa, permite reintentar y conserva el análisis anterior', async () => {
    withLatestAnalysis(exampleAnalysis({ score: 68 }));
    server.use(
      http.post(`${API}/offers/:offerId/analysis`, () =>
        problem(504, 'llm-timeout', 'La IA no ha respondido a tiempo', 'Inténtalo de nuevo en unos segundos.'),
      ),
    );
    const { user } = renderDetail();
    const panel = await findPanel();
    await within(panel).findByTestId('analysis-score');

    await user.click(within(panel).getByRole('button', { name: 'Volver a calcular' }));

    const error = await within(panel).findByTestId('analysis-error');
    expect(error).toHaveTextContent('La IA no ha respondido a tiempo');
    expect(error).toHaveTextContent('Inténtalo de nuevo en unos segundos.');
    expect(within(panel).getByTestId('analysis-score')).toHaveTextContent('68');
    expect(within(panel).getByRole('button', { name: 'Volver a calcular' })).toBeEnabled();
  });

  it('si la IA está saturada (503) indica cuándo volver a intentarlo', async () => {
    server.use(
      http.post(
        `${API}/offers/:offerId/analysis`,
        () =>
          new HttpResponse(
            JSON.stringify({
              type: 'https://jobpilot.dev/problems/llm-overloaded',
              title: 'La IA está saturada',
              status: 503,
              detail: 'Hay demasiadas peticiones a la IA en este momento.',
            }),
            { status: 503, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '30' } },
          ),
      ),
    );
    const { user } = renderDetail();
    const panel = await findPanel();

    await user.click(await within(panel).findByRole('button', { name: 'Calcular encaje' }));

    const error = await within(panel).findByTestId('analysis-error');
    expect(error).toHaveTextContent('La IA está saturada');
    expect(error).toHaveTextContent('Vuelve a intentarlo dentro de 30 segundos.');
    expect(within(panel).getByRole('button', { name: 'Calcular encaje' })).toBeEnabled();
  });

  it('con el límite de IA superado (429) indica la espera en minutos', async () => {
    server.use(
      http.post(
        `${API}/offers/:offerId/analysis`,
        () =>
          new HttpResponse(
            JSON.stringify({
              type: 'https://jobpilot.dev/problems/rate-limit-exceeded',
              title: 'Demasiadas peticiones',
              status: 429,
              detail: 'Has superado el límite de consultas a la IA.',
            }),
            { status: 429, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '120' } },
          ),
      ),
    );
    const { user } = renderDetail();
    const panel = await findPanel();

    await user.click(await within(panel).findByRole('button', { name: 'Calcular encaje' }));

    const error = await within(panel).findByTestId('analysis-error');
    expect(error).toHaveTextContent('Demasiadas peticiones');
    expect(error).toHaveTextContent('Vuelve a intentarlo dentro de 2 minutos.');
  });

  it('si la oferta ha dejado de tener requisitos (409 offer-not-extracted) recarga la oferta', async () => {
    let extracted = true;
    server.use(
      http.get(`${API}/offers/:offerId`, () =>
        HttpResponse.json(extracted ? exampleOffer() : failedOffer()),
      ),
      http.post(`${API}/offers/:offerId/analysis`, () => {
        extracted = false;
        return problem(
          409,
          'offer-not-extracted',
          'La oferta no tiene requisitos extraídos',
          'Extrae los requisitos antes de calcular el encaje.',
        );
      }),
    );
    const { user } = renderDetail();
    const panel = await findPanel();

    await user.click(await within(panel).findByRole('button', { name: 'Calcular encaje' }));

    expect(await screen.findByTestId('extraction-failed')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Análisis de encaje' })).not.toBeInTheDocument();
  });

  it('si no se puede cargar el último análisis permite reintentarlo', async () => {
    let failing = true;
    server.use(
      http.get(`${API}/offers/:offerId/analysis`, () =>
        failing
          ? problem(500, 'internal-error', 'Error interno', 'Ha ocurrido un error inesperado.')
          : HttpResponse.json(exampleAnalysis()),
      ),
    );
    // Sin los reintentos automáticos de los 5xx, para no esperar su retardo.
    const queryClient = createQueryClient();
    queryClient.setDefaultOptions({ queries: { ...queryClient.getDefaultOptions().queries, retry: false } });
    const { user } = renderDetail(queryClient);
    const panel = await findPanel();

    const error = await within(panel).findByTestId('analysis-load-error');
    expect(error).toHaveTextContent('No se ha podido cargar el análisis');

    failing = false;
    await user.click(within(panel).getByRole('button', { name: 'Reintentar' }));

    expect(await within(panel).findByTestId('analysis-score')).toHaveTextContent('85');
  });

  it('una oferta cuya extracción ha fallado no muestra el panel ni pide el análisis', async () => {
    let analysisRequested = false;
    server.use(
      http.get(`${API}/offers/:offerId`, () => HttpResponse.json(failedOffer())),
      http.get(`${API}/offers/:offerId/analysis`, () => {
        analysisRequested = true;
        return HttpResponse.json(exampleAnalysis());
      }),
    );
    renderDetail();

    expect(await screen.findByTestId('extraction-failed')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Análisis de encaje' })).not.toBeInTheDocument();
    expect(analysisRequested).toBe(false);
  });

  it('tras calcular el encaje refresca el dashboard para mostrar la nueva puntuación', async () => {
    let listRequests = 0;
    server.use(
      http.get(`${API}/offers`, () => {
        listRequests += 1;
        return HttpResponse.json([]);
      }),
    );
    const { user, queryClient } = renderDetail();
    // El dashboard ya estaba en caché (el usuario venía de él).
    await queryClient.prefetchQuery({ queryKey: ['offers', 'list'], queryFn: () => [] });
    const panel = await findPanel();

    await user.click(await within(panel).findByRole('button', { name: 'Calcular encaje' }));
    await within(panel).findByTestId('analysis-score');

    expect(queryClient.getQueryState(['offers', 'list'])?.isInvalidated).toBe(true);
    expect(listRequests).toBe(0); // no está montado: se recargará al volver
  });
});

