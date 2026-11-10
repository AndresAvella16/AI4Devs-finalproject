import { screen, waitFor, within } from '@testing-library/react';
import { delay, http } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest';
import {
  APPLICATION_STATUSES,
  type ApplicationStatus,
  type ChangeStatusRequest,
  type UpdateApplicationRequest,
} from '../../api/applications';
import { saveSession } from '../../api/session';
import { createQueryClient } from '../../app/queryClient';
import {
  API,
  authResponse,
  problem,
  resetApplicationStore,
  nth,
  seedApplication,
  seedApplications,
  storedApplications,
} from '../../test/handlers';
import { renderWithProviders } from '../../test/renderWithProviders';
import { server } from '../../test/server';

const angular = seedApplication(1);
const kotlin = seedApplication(2);
const fullStack = seedApplication(3);

function renderBoard() {
  saveSession(authResponse());
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({ queries: { ...queryClient.getDefaultOptions().queries, retry: false } });
  return renderWithProviders({ path: '/tablero', queryClient });
}

function column(status: ApplicationStatus): HTMLElement {
  return screen.getByTestId(`kanban-column-${status}`);
}

function cardTitles(status: ApplicationStatus): string[] {
  return within(column(status))
    .queryAllByTestId('application-card')
    .map((card) => within(card).getByTestId('application-card-title').textContent ?? '');
}

/** Peticiones de escritura que llegan a la API, para comprobar a qué endpoint va cada movimiento. */
function recordWrites() {
  const writes: { path: string; body: unknown }[] = [];
  server.events.on('request:start', async ({ request }) => {
    if (request.method === 'PATCH') {
      writes.push({ path: new URL(request.url).pathname, body: await request.clone().json() });
    }
  });
  return writes;
}

/**
 * jsdom no calcula posiciones: se da a cada columna y a cada tarjeta un rectángulo según su `data-status` y
 * `data-index`, como si el tablero estuviera pintado (columnas de 280 px cada 300 px; tarjetas de 100 px cada 110 px).
 * El `DragOverlay` (con `position: fixed`) se pinta donde dicen sus estilos `top`/`left`/`width`/`height`, que dnd-kit
 * copia de la tarjeta cogida; dnd-kit mide su primer hijo. Así funciona el `KeyboardSensor` real de dnd-kit.
 */
function stubBoardLayout() {
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
    const element = this as HTMLElement;
    const overlay = [element, element.parentElement].find((node) => node?.style.position === 'fixed');
    if (overlay) {
      const { top, left, width, height } = overlay.style;
      return new DOMRect(parseFloat(left), parseFloat(top), parseFloat(width), parseFloat(height));
    }
    const status = element.dataset.status as ApplicationStatus | undefined;
    const columnIndex = status ? APPLICATION_STATUSES.indexOf(status) : -1;
    if (columnIndex < 0) return new DOMRect(0, 0, 0, 0);
    if (element.dataset.index !== undefined) {
      return new DOMRect(columnIndex * 300 + 10, 60 + Number(element.dataset.index) * 110, 260, 100);
    }
    return new DOMRect(columnIndex * 300, 50, 280, 800);
  });
  Element.prototype.scrollIntoView = () => undefined;
}

function dragHandle(title: string): HTMLElement {
  return screen.getByRole('button', { name: `Mover la candidatura ${title}` });
}

let consoleError: MockInstance;
let consoleWarn: MockInstance;

beforeEach(() => {
  consoleError = vi.spyOn(console, 'error');
  consoleWarn = vi.spyOn(console, 'warn');
});

afterEach(() => {
  server.events.removeAllListeners();
  vi.unstubAllGlobals();
  // Criterio: ni errores ni avisos en la consola.
  expect(consoleError).not.toHaveBeenCalled();
  expect(consoleWarn).not.toHaveBeenCalled();
});

describe('KanbanBoard — ver el tablero', () => {
  it('pinta las 5 columnas con su contador y las tarjetas ordenadas por boardPosition', async () => {
    // La API las manda desordenadas dentro de GUARDADA: el orden lo da boardPosition.
    resetApplicationStore([{ ...kotlin, boardPosition: 0 }, { ...angular, boardPosition: 1 }, ...seedApplications().slice(2)]);
    renderBoard();

    expect(await screen.findByRole('heading', { level: 1, name: 'Tablero' })).toBeInTheDocument();
    await screen.findAllByTestId('application-card');
    const headings = APPLICATION_STATUSES.map((status) => within(column(status)).getByRole('heading', { level: 2 }).textContent);
    expect(headings).toEqual(['Guardada', 'Enviada', 'Entrevista', 'Oferta', 'Descartada']);

    expect(within(column('GUARDADA')).getByTestId('kanban-column-count')).toHaveTextContent('2');
    expect(within(column('ENVIADA')).getByTestId('kanban-column-count')).toHaveTextContent('1');
    expect(within(column('DESCARTADA')).getByTestId('kanban-column-count')).toHaveTextContent('0');
    expect(cardTitles('GUARDADA')).toEqual(['Backend Developer Kotlin', 'Desarrollador/a Frontend Senior (Angular)']);
  });

  it('cada tarjeta muestra puesto, empresa, puntuación y fecha de seguimiento', async () => {
    renderBoard();
    await screen.findAllByTestId('application-card');

    const java = within(column('ENTREVISTA')).getByTestId('application-card');
    expect(java).toHaveTextContent('Desarrollador/a Java Junior');
    expect(java).toHaveTextContent('Innova Software Ficticio');
    expect(within(java).getByTestId('match-score-badge')).toHaveTextContent('85');
    expect(within(java).getByTestId('application-follow-up')).toHaveTextContent('20 nov 2026');

    // Oferta sin extraer: sin título ni empresa.
    expect(within(column('OFERTA')).getByTestId('application-card')).toHaveTextContent('Oferta sin título');
  });

  it('solo las tarjetas con seguimiento vencido muestran el indicador', async () => {
    renderBoard();
    await screen.findAllByTestId('application-card');

    const overdue = screen.getAllByTestId('follow-up-overdue');
    expect(overdue).toHaveLength(1);
    expect(nth(overdue, 0)).toHaveTextContent('Seguimiento pendiente');
    expect(within(column('ENVIADA')).getByTestId('follow-up-overdue')).toBeInTheDocument();
  });

  it('sin candidaturas muestra el estado vacío con enlace al formulario de oferta', async () => {
    resetApplicationStore([]);
    renderBoard();

    const empty = await screen.findByTestId('board-empty');
    expect(empty).toHaveTextContent('Aún no tienes candidaturas');
    expect(within(empty).getByRole('link', { name: /importa una oferta/i })).toHaveAttribute('href', '/ofertas/nueva');
  });

  it('si falla la carga muestra el error y permite reintentar', async () => {
    server.use(
      http.get(`${API}/applications`, () => problem(500, 'internal-error', 'Error interno', 'Algo ha fallado.'), {
        once: true,
      }),
    );
    const { user } = renderBoard();

    const error = await screen.findByTestId('board-error');
    expect(error).toHaveTextContent('Algo ha fallado.');
    await user.click(within(error).getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findAllByTestId('application-card')).toHaveLength(5);
  });

  it('cada tarjeta lleva el id de su oferta en data-offer-id (los E2E la localizan así, JP-QA-01)', async () => {
    renderBoard();
    const cards = await screen.findAllByTestId('application-card');

    expect(cards.map((card) => card.dataset.offerId).sort()).toEqual(
      storedApplications().map((application) => application.offerId).sort(),
    );
  });
});

describe('KanbanBoard — mover tarjetas con el teclado', () => {
  beforeEach(stubBoardLayout);

  it('de Guardada a Enviada: se mueve al instante con PATCH /status y se mantiene al recargar', async () => {
    const writes = recordWrites();
    server.use(
      http.patch(`${API}/applications/:applicationId/status`, async () => {
        await delay(50);
        return undefined; // pasa al handler por defecto
      }),
    );
    const { user, unmount } = renderBoard();
    await screen.findAllByTestId('application-card');

    dragHandle(angular.offerTitle ?? '').focus();
    await user.keyboard('[Space]');
    await user.keyboard('[ArrowRight]');
    await user.keyboard('[Space]');

    // Actualización optimista: ya está en Enviada antes de que responda el backend.
    expect(cardTitles('ENVIADA')).toContain('Desarrollador/a Frontend Senior (Angular)');
    expect(cardTitles('GUARDADA')).toEqual(['Backend Developer Kotlin']);
    await waitFor(() => expect(writes).toHaveLength(1));
    expect(nth(writes, 0)).toEqual({
      path: `/api/v1/applications/${angular.id}/status`,
      body: { status: 'ENVIADA', boardPosition: 0, version: 0 } satisfies ChangeStatusRequest,
    });

    // Recargar cuando el servidor ya lo ha guardado.
    await waitFor(() => expect(storedApplications().find((card) => card.id === angular.id)?.status).toBe('ENVIADA'));
    unmount();
    renderBoard();
    await screen.findAllByTestId('application-card');
    expect(cardTitles('ENVIADA')).toEqual(['Desarrollador/a Frontend Senior (Angular)', 'Desarrollador/a Full Stack']);
    expect(within(column('GUARDADA')).getByTestId('kanban-column-count')).toHaveTextContent('1');
  });

  it('al reordenar dentro de una columna llama a PATCH /applications/{id}, no a /status', async () => {
    const writes = recordWrites();
    const { user } = renderBoard();
    await screen.findAllByTestId('application-card');

    dragHandle(kotlin.offerTitle ?? '').focus();
    await user.keyboard('[Space]');
    await user.keyboard('[ArrowUp]');
    await user.keyboard('[Space]');

    expect(cardTitles('GUARDADA')).toEqual(['Backend Developer Kotlin', 'Desarrollador/a Frontend Senior (Angular)']);
    await waitFor(() => expect(writes).toHaveLength(1));
    expect(nth(writes, 0)).toEqual({
      path: `/api/v1/applications/${kotlin.id}`,
      body: { boardPosition: 0, version: 2 } satisfies UpdateApplicationRequest,
    });
    expect(writes.some((write) => write.path.endsWith('/status'))).toBe(false);
  });

  it('mientras se arrastra, las columnas a las que no se puede pasar se marcan como no válidas', async () => {
    const { user } = renderBoard();
    await screen.findAllByTestId('application-card');

    dragHandle(angular.offerTitle ?? '').focus();
    await user.keyboard('[Space]');

    expect(column('GUARDADA')).toHaveAttribute('data-drop-allowed', 'true');
    expect(column('ENVIADA')).toHaveAttribute('data-drop-allowed', 'true');
    expect(column('ENTREVISTA')).toHaveAttribute('data-drop-allowed', 'false');
    expect(column('OFERTA')).toHaveAttribute('data-drop-allowed', 'false');
    expect(column('DESCARTADA')).toHaveAttribute('data-drop-allowed', 'true');

    await user.keyboard('[Escape]');
    expect(column('OFERTA')).not.toHaveAttribute('data-drop-allowed');
  });

  it('soltar en una columna no permitida no llama al backend: la tarjeta vuelve y un toast explica por qué', async () => {
    const writes = recordWrites();
    const { user } = renderBoard();
    await screen.findAllByTestId('application-card');

    // Guardada → (Enviada) → Entrevista: saltarse un estado no está permitido.
    dragHandle(angular.offerTitle ?? '').focus();
    await user.keyboard('[Space]');
    await user.keyboard('[ArrowRight]');
    await user.keyboard('[ArrowRight]');
    expect(column('ENTREVISTA')).toHaveAttribute('data-drop-allowed', 'false');
    await user.keyboard('[Space]');

    const toast = await screen.findByTestId('toast');
    expect(toast).toHaveTextContent('Movimiento no permitido');
    expect(toast).toHaveTextContent('No se puede pasar de Guardada a Entrevista.');
    expect(cardTitles('GUARDADA')).toEqual(['Desarrollador/a Frontend Senior (Angular)', 'Backend Developer Kotlin']);
    expect(cardTitles('ENTREVISTA')).toEqual(['Desarrollador/a Java Junior']);
    // Margen para que una petición llegara si se hubiera enviado.
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(writes).toEqual([]);
  });

  it('red de seguridad: si el backend responde 409 a un movimiento permitido en cliente, la tarjeta vuelve y se ve el detail', async () => {
    server.use(
      http.patch(`${API}/applications/:applicationId/status`, async () => {
        await delay(50);
        return problem(409, 'invalid-status-transition', 'Transición de estado no permitida', 'No se puede pasar de GUARDADA a ENVIADA.');
      }),
    );
    const { user } = renderBoard();
    await screen.findAllByTestId('application-card');

    dragHandle(angular.offerTitle ?? '').focus();
    await user.keyboard('[Space]');
    await user.keyboard('[ArrowRight]');
    await user.keyboard('[Space]');
    expect(cardTitles('ENVIADA')).toContain('Desarrollador/a Frontend Senior (Angular)');

    const toast = await screen.findByTestId('toast');
    expect(toast).toHaveTextContent('No se puede pasar de GUARDADA a ENVIADA.');
    expect(cardTitles('GUARDADA')).toEqual(['Desarrollador/a Frontend Senior (Angular)', 'Backend Developer Kotlin']);
    expect(cardTitles('ENVIADA')).toEqual(['Desarrollador/a Full Stack']);
  });
});

describe('KanbanBoard — pantallas de menos de 768 px', () => {
  beforeEach(() => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
  });

  it('las columnas pasan a pestañas y se mueve con "Mover a…" (solo destinos permitidos, al final)', async () => {
    const writes = recordWrites();
    const { user } = renderBoard();

    const tabs = await screen.findByRole('tablist', { name: 'Columnas del tablero' });
    expect(within(tabs).getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
      'Guardada2',
      'Enviada1',
      'Entrevista1',
      'Oferta1',
      'Descartada0',
    ]);
    expect(within(tabs).getByRole('tab', { name: /Guardada/ })).toHaveAttribute('aria-selected', 'true');

    const panel = screen.getByRole('tabpanel');
    const first = nth(within(panel).getAllByTestId('application-card'), 0);
    const guardadas = storedApplications().filter((application) => application.status === 'GUARDADA');
    expect(guardadas.map((application) => application.offerId)).toContain(first.dataset.offerId);
    const select = within(first).getByLabelText('Mover a…');
    expect(within(select).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Elige columna',
      'Enviada',
      'Descartada',
    ]);
    await user.selectOptions(select, 'ENVIADA');
    await user.click(within(first).getByRole('button', { name: 'Mover' }));

    await waitFor(() => expect(writes).toHaveLength(1));
    expect(nth(writes, 0).body).toEqual({ status: 'ENVIADA', boardPosition: 1, version: 0 });

    await user.click(within(tabs).getByRole('tab', { name: /Enviada/ }));
    const enviadas = within(screen.getByRole('tabpanel')).getAllByTestId('application-card-title');
    expect(enviadas.map((title) => title.textContent)).toEqual([
      'Desarrollador/a Full Stack',
      'Desarrollador/a Frontend Senior (Angular)',
    ]);
  });
});

describe('ApplicationDrawer — notas y fechas', () => {
  it('al hacer clic en una tarjeta se abre el panel y guarda con PATCH /applications/{id}', async () => {
    const writes = recordWrites();
    const { user } = renderBoard();
    await screen.findAllByTestId('application-card');

    await user.click(within(column('ENVIADA')).getByRole('button', { name: /Ver detalles de Desarrollador\/a Full Stack/ }));
    const drawer = await screen.findByRole('dialog', { name: 'Desarrollador/a Full Stack' });
    expect(within(drawer).getByLabelText('Notas')).toHaveValue('Escribir a la recruiter.');
    expect(within(drawer).getByLabelText('Fecha de envío')).toHaveValue('2026-10-29');
    expect(within(drawer).getByLabelText('Fecha de seguimiento')).toHaveValue('2026-11-05');
    expect(within(drawer).getByRole('link', { name: 'Ver la oferta' })).toHaveAttribute('href', `/ofertas/${fullStack.offerId}`);

    await user.clear(within(drawer).getByLabelText('Notas'));
    await user.type(within(drawer).getByLabelText('Notas'), 'Llamada el lunes');
    await user.clear(within(drawer).getByLabelText('Fecha de seguimiento'));
    await user.type(within(drawer).getByLabelText('Fecha de seguimiento'), '2026-11-17');
    await user.click(within(drawer).getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(writes).toEqual([
      {
        path: `/api/v1/applications/${fullStack.id}`,
        body: { notes: 'Llamada el lunes', appliedAt: '2026-10-29', followUpDate: '2026-11-17', version: 1 },
      },
    ]);
    // El seguimiento ya no está vencido.
    await waitFor(() => expect(screen.queryByTestId('follow-up-overdue')).not.toBeInTheDocument());
  });

  it('valida en cliente: la fecha de envío es obligatoria en Enviada y las notas tienen un máximo', async () => {
    const writes = recordWrites();
    const { user } = renderBoard();
    await screen.findAllByTestId('application-card');

    await user.click(within(column('ENVIADA')).getByRole('button', { name: /Ver detalles de/ }));
    const drawer = await screen.findByRole('dialog');
    await user.clear(within(drawer).getByLabelText('Fecha de envío'));
    await user.click(within(drawer).getByRole('button', { name: 'Guardar' }));

    expect(await within(drawer).findByText('La fecha de envío es obligatoria en una candidatura Enviada')).toBeInTheDocument();
    expect(within(drawer).getByLabelText('Fecha de envío')).toHaveAttribute('aria-invalid', 'true');
    expect(writes).toHaveLength(0);
  });

  it('se cierra con Escape y devuelve el foco a la tarjeta', async () => {
    const { user } = renderBoard();
    await screen.findAllByTestId('application-card');

    const open = nth(within(column('GUARDADA')).getAllByRole('button', { name: /Ver detalles de/ }), 0);
    await user.click(open);
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await user.keyboard('[Escape]');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(open).toHaveFocus();
  });
});
