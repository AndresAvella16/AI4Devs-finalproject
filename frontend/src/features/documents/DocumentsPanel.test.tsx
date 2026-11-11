import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DocumentType } from '../../api/documents';
import { saveSession } from '../../api/session';
import {
  addDocument,
  API,
  authResponse,
  EXAMPLE_LETTER,
  EXAMPLE_OFFER_ID,
  EXAMPLE_TAILORED_CV,
  exampleAnalysis,
  failedOffer,
  problem,
  storedDocuments,
} from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

/** Lo que se ha "descargado": nombre del `<a download>` y contenido del blob. */
let downloads: { fileName: string; blob: Blob }[] = [];
const blobs = new Map<string, Blob>();

beforeEach(() => {
  downloads = [];
  blobs.clear();
  // jsdom no implementa las URL de blob ni la navegación de los enlaces.
  URL.createObjectURL = vi.fn((blob: Blob) => {
    const url = `blob:test/${blobs.size + 1}`;
    blobs.set(url, blob);
    return url;
  });
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    const blob = blobs.get(this.href);
    if (blob !== undefined) downloads.push({ fileName: this.download, blob });
  });
});

afterEach(() => vi.restoreAllMocks());

function withAnalysis() {
  server.use(http.get(`${API}/offers/:offerId/analysis`, () => HttpResponse.json(exampleAnalysis())));
}

function renderDetail() {
  saveSession(authResponse());
  return renderWithProviders({ path: `/ofertas/${EXAMPLE_OFFER_ID}` });
}

async function findPanel() {
  return screen.findByRole('region', { name: 'Documentos' });
}

/** Panel de la pestaña visible ("Carta" o "CV adaptado"). */
async function findTab(name: 'Carta' | 'CV adaptado') {
  return screen.findByRole('tabpanel', { name });
}

async function findEditor(tab: HTMLElement) {
  return within(tab).findByTestId('document-editor');
}

function letterV1(content = EXAMPLE_LETTER) {
  return addDocument('CARTA', content, false);
}

describe('DocumentsPanel — carta y CV adaptado (HU-04)', () => {
  it('tiene las pestañas "Carta" y "CV adaptado", que se cambian con las flechas del teclado', async () => {
    withAnalysis();
    const { user } = renderDetail();
    const panel = await findPanel();

    const tabs = within(panel).getAllByRole('tab');
    expect(tabs.map((tab) => tab.textContent)).toEqual(['Carta', 'CV adaptado']);
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
    expect(await findTab('Carta')).toBeVisible();

    await user.click(screen.getByRole('tab', { name: 'Carta' }));
    await user.keyboard('{ArrowRight}');
    expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
    expect(tabs[1]).toHaveFocus();
    expect(await findTab('CV adaptado')).toBeVisible();
    expect(screen.queryByRole('tabpanel', { name: 'Carta' })).not.toBeInTheDocument();

    await user.keyboard('{ArrowRight}');
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
    expect(tabs[0]).toHaveFocus();
  });

  it('sin análisis de encaje no deja generar y explica por qué', async () => {
    renderDetail();
    const tab = await findTab('Carta');

    const button = await within(tab).findByRole('button', { name: 'Generar carta' });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription('Calcula primero tu encaje con la oferta para generar la carta.');
  });

  it('al pulsar "Generar carta" muestra la carta en el editor como versión 1, generada por la IA', async () => {
    withAnalysis();
    let posts: { type: DocumentType }[] = [];
    server.use(
      http.post(`${API}/offers/:offerId/documents`, async ({ request }) => {
        posts = [...posts, (await request.json()) as { type: DocumentType }];
        await delay(50);
        return HttpResponse.json(addDocument('CARTA', EXAMPLE_LETTER, false), { status: 201 });
      }),
    );
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    expect(await within(tab).findByTestId('document-empty')).toHaveTextContent('Aún no has generado la carta');

    await user.click(await within(tab).findByRole('button', { name: 'Generar carta' }));

    expect(within(tab).getByTestId('document-generating')).toHaveTextContent('Generando la carta…');
    expect(within(tab).getByRole('button', { name: /Generando/ })).toBeDisabled();

    const editor = await findEditor(tab);
    expect(posts).toEqual([{ type: 'CARTA' }]);
    expect(editor).toHaveValue(EXAMPLE_LETTER);
    expect(editor).toHaveAccessibleName('Texto de la carta (Markdown)');
    expect(within(tab).getByTestId('document-version')).toHaveTextContent('Versión 1 · Generada por la IA');
    expect(within(tab).getByRole('button', { name: 'Volver a generar' })).toBeEnabled();
    expect(within(tab).getAllByTestId('document-version-item')).toHaveLength(1);
  });

  it('carga la última versión guardada y su historial, de la más reciente a la más antigua', async () => {
    withAnalysis();
    letterV1();
    addDocument('CARTA', 'Versión editada', true);
    renderDetail();
    const tab = await findTab('Carta');

    expect(await findEditor(tab)).toHaveValue('Versión editada');
    expect(within(tab).getByTestId('document-version')).toHaveTextContent('Versión 2 · Editada por mí');
    const items = within(tab).getAllByTestId('document-version-item');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Versión 2 · Editada por mí'),
      expect.stringContaining('Versión 1 · Generada por la IA'),
    ]);
  });

  it('al editar y guardar crea una versión nueva marcada como "Editada por mí"', async () => {
    withAnalysis();
    const v1 = letterV1();
    const seen: { documentId: string; contentMarkdown: string }[] = [];
    server.events.on('request:start', async ({ request }) => {
      const match = /\/documents\/([^/]+)\/versions$/.exec(new URL(request.url).pathname);
      if (match && request.method === 'POST') {
        seen.push({ documentId: match[1] ?? '', ...((await request.clone().json()) as { contentMarkdown: string }) });
      }
    });
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    const editor = await findEditor(tab);
    const save = within(tab).getByRole('button', { name: 'Guardar cambios' });
    expect(save).toBeDisabled();

    await user.clear(editor);
    await user.type(editor, 'Mi carta revisada');
    await user.click(save);

    await waitFor(() => expect(within(tab).getByTestId('document-version')).toHaveTextContent('Versión 2 · Editada por mí'));
    server.events.removeAllListeners();
    expect(seen).toEqual([{ documentId: v1.id, contentMarkdown: 'Mi carta revisada' }]);
    expect(storedDocuments('CARTA').map((document) => document.editedByUser)).toEqual([false, true]);
    expect(await findEditor(tab)).toHaveValue('Mi carta revisada');
    expect(within(tab).getByRole('button', { name: 'Guardar cambios' })).toBeDisabled();
    expect(within(tab).getAllByTestId('document-version-item')[0]).toHaveTextContent('Versión 2 · Editada por mí');
    expect(await screen.findByTestId('toast')).toHaveTextContent('Versión 2 de la carta guardada');
  });

  it('no deja guardar un texto vacío, solo con espacios o de más de 20.000 caracteres', async () => {
    withAnalysis();
    letterV1();
    let posts = 0;
    server.use(
      http.post(`${API}/documents/:documentId/versions`, () => {
        posts += 1;
        return HttpResponse.json({}, { status: 500 });
      }),
    );
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    const editor = await findEditor(tab);
    const save = within(tab).getByRole('button', { name: 'Guardar cambios' });

    await user.clear(editor);
    await user.type(editor, '   ');
    await user.click(save);
    expect(await within(tab).findByText('Escribe el contenido del documento.')).toBeInTheDocument();
    expect(editor).toHaveAttribute('aria-invalid', 'true');

    await user.clear(editor);
    await user.click(editor);
    await user.paste('a'.repeat(20_001));
    expect(within(tab).getByTestId('document-char-counter')).toHaveTextContent('20.001 / 20.000 caracteres');
    await user.click(save);
    expect(await within(tab).findByText('El documento no puede superar los 20.000 caracteres.')).toBeInTheDocument();
    expect(posts).toBe(0);
  });

  it('con cambios sin guardar desactiva "Exportar PDF" y lo explica', async () => {
    withAnalysis();
    letterV1();
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    const editor = await findEditor(tab);
    expect(within(tab).getByRole('button', { name: 'Exportar PDF' })).toBeEnabled();

    await user.type(editor, ' Un cambio.');

    const exportButton = within(tab).getByRole('button', { name: 'Exportar PDF' });
    expect(exportButton).toBeDisabled();
    expect(exportButton).toHaveAccessibleDescription('Guarda los cambios para exportarlos.');
    expect(within(tab).getByRole('button', { name: 'Guardar cambios' })).toBeEnabled();
  });

  it('con 409 document-version-conflict avisa, conserva el texto y deja cargar la última versión', async () => {
    withAnalysis();
    letterV1();
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    const editor = await findEditor(tab);
    // Otra pestaña guarda la versión 2 mientras tanto.
    addDocument('CARTA', 'Versión de otra pestaña', true);

    await user.type(editor, ' Mi cambio.');
    await user.click(within(tab).getByRole('button', { name: 'Guardar cambios' }));

    const conflict = await within(tab).findByTestId('document-conflict');
    expect(conflict).toHaveTextContent('El documento ha cambiado');
    expect(editor).toHaveValue(`${EXAMPLE_LETTER} Mi cambio.`);
    expect(within(tab).getByTestId('document-version')).toHaveTextContent('Versión 1');

    await user.click(within(conflict).getByRole('button', { name: 'Cargar la última versión' }));

    await waitFor(() => expect(within(tab).getByTestId('document-version')).toHaveTextContent('Versión 2 · Editada por mí'));
    expect(await findEditor(tab)).toHaveValue('Versión de otra pestaña');
    expect(within(tab).queryByTestId('document-conflict')).not.toBeInTheDocument();
  });

  it('"Volver a generar" con cambios sin guardar pide confirmación; sin cambios, genera directamente', async () => {
    withAnalysis();
    letterV1();
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    const editor = await findEditor(tab);

    await user.type(editor, ' Borrador.');
    await user.click(within(tab).getByRole('button', { name: 'Volver a generar' }));
    const confirm = within(tab).getByTestId('document-regenerate-confirm');
    expect(confirm).toHaveTextContent('Perderás los cambios que no has guardado');
    await user.click(within(confirm).getByRole('button', { name: 'Cancelar' }));
    expect(within(tab).queryByTestId('document-regenerate-confirm')).not.toBeInTheDocument();
    expect(editor).toHaveValue(`${EXAMPLE_LETTER} Borrador.`);
    expect(storedDocuments('CARTA')).toHaveLength(1);

    await user.click(within(tab).getByRole('button', { name: 'Volver a generar' }));
    await user.click(within(tab).getByRole('button', { name: 'Sí, volver a generar' }));
    await waitFor(() => expect(within(tab).getByTestId('document-version')).toHaveTextContent('Versión 2 · Generada por la IA'));
    expect(await findEditor(tab)).toHaveValue(EXAMPLE_LETTER);

    await user.click(within(tab).getByRole('button', { name: 'Volver a generar' }));
    await waitFor(() => expect(within(tab).getByTestId('document-version')).toHaveTextContent('Versión 3 · Generada por la IA'));
    expect(within(tab).queryByTestId('document-regenerate-confirm')).not.toBeInTheDocument();
  });

  it('si la IA falla muestra el error con la espera del Retry-After y deja reintentar', async () => {
    withAnalysis();
    let fail = true;
    server.use(
      http.post(`${API}/offers/:offerId/documents`, () =>
        fail
          ? HttpResponse.json(
              {
                type: 'https://jobpilot.dev/problems/llm-overloaded',
                title: 'La IA está saturada',
                status: 503,
                detail: 'Inténtalo de nuevo en unos segundos.',
              },
              { status: 503, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '30' } },
            )
          : HttpResponse.json(addDocument('CARTA', EXAMPLE_LETTER, false), { status: 201 }),
      ),
    );
    const { user } = renderDetail();
    const tab = await findTab('Carta');

    await user.click(await within(tab).findByRole('button', { name: 'Generar carta' }));

    const error = await within(tab).findByTestId('document-generate-error');
    expect(error).toHaveTextContent('La IA está saturada');
    expect(error).toHaveTextContent('Vuelve a intentarlo dentro de 30 segundos.');

    fail = false;
    await user.click(within(tab).getByRole('button', { name: 'Generar carta' }));
    expect(await findEditor(tab)).toHaveValue(EXAMPLE_LETTER);
    expect(within(tab).queryByTestId('document-generate-error')).not.toBeInTheDocument();
  });

  it('con 409 analysis-required muestra el motivo', async () => {
    withAnalysis();
    server.use(
      http.post(`${API}/offers/:offerId/documents`, () =>
        problem(409, 'analysis-required', 'La oferta no tiene análisis de encaje', 'Calcula primero el encaje.'),
      ),
    );
    const { user } = renderDetail();
    const tab = await findTab('Carta');

    await user.click(await within(tab).findByRole('button', { name: 'Generar carta' }));

    expect(await within(tab).findByTestId('document-generate-error')).toHaveTextContent('Calcula primero el encaje.');
  });

  it('"Exportar PDF" descarga el PDF de la versión mostrada con el nombre del servidor', async () => {
    withAnalysis();
    letterV1();
    addDocument('CARTA', 'Segunda versión', true);
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    await findEditor(tab);

    await user.click(within(tab).getByRole('button', { name: 'Exportar PDF' }));

    await waitFor(() => expect(downloads).toHaveLength(1));
    const [download] = downloads;
    expect(download?.fileName).toBe('carta-v2.pdf');
    expect(await download?.blob.text()).toMatch(/^%PDF-/);
    // La URL blob: se libera después del clic.
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test/1'));
  });

  it('si falla la exportación muestra un aviso', async () => {
    withAnalysis();
    letterV1();
    server.use(
      http.get(`${API}/documents/:documentId/pdf`, () =>
        problem(404, 'not-found', 'Recurso no encontrado', 'El documento no existe.'),
      ),
    );
    const { user } = renderDetail();
    const tab = await findTab('Carta');
    await findEditor(tab);

    await user.click(within(tab).getByRole('button', { name: 'Exportar PDF' }));

    const error = await within(tab).findByTestId('document-export-error');
    expect(error).toHaveTextContent('No se ha podido exportar el PDF');
    expect(error).toHaveTextContent('El documento no existe.');
    expect(downloads).toHaveLength(0);
  });

  it('la carta y el CV adaptado son independientes, y los cambios sin guardar se conservan al cambiar de pestaña', async () => {
    withAnalysis();
    letterV1();
    const { user } = renderDetail();
    const letterTab = await findTab('Carta');
    await user.type(await findEditor(letterTab), ' Sin guardar.');

    await user.click(screen.getByRole('tab', { name: 'CV adaptado' }));
    const cvTab = await findTab('CV adaptado');
    expect(await within(cvTab).findByTestId('document-empty')).toHaveTextContent('Aún no has generado el CV adaptado');
    await user.click(within(cvTab).getByRole('button', { name: 'Generar CV adaptado' }));
    const cvEditor = await findEditor(cvTab);
    expect(cvEditor).toHaveValue(EXAMPLE_TAILORED_CV);
    expect(cvEditor).toHaveAccessibleName('Texto del CV adaptado (Markdown)');
    expect(within(cvTab).getByTestId('document-version')).toHaveTextContent('Versión 1 · Generada por la IA');

    await user.click(screen.getByRole('tab', { name: 'Carta' }));
    expect(await findEditor(await findTab('Carta'))).toHaveValue(`${EXAMPLE_LETTER} Sin guardar.`);
    expect(storedDocuments('CARTA')).toHaveLength(1);
  });

  it('el contenido de la IA se muestra como texto: nunca se pinta como HTML', async () => {
    withAnalysis();
    letterV1('Hola <img src="x" data-testid="injected" onerror="alert(1)"> **negrita**');
    renderDetail();
    const tab = await findTab('Carta');

    expect(await findEditor(tab)).toHaveValue('Hola <img src="x" data-testid="injected" onerror="alert(1)"> **negrita**');
    expect(screen.queryByTestId('injected')).not.toBeInTheDocument();
    expect(document.querySelector('img')).toBeNull();
  });

  it('no aparece en una oferta cuya extracción no está COMPLETADA', async () => {
    server.use(http.get(`${API}/offers/:offerId`, () => HttpResponse.json(failedOffer())));
    renderDetail();

    expect(await screen.findByTestId('extraction-failed')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Documentos' })).not.toBeInTheDocument();
  });
});
