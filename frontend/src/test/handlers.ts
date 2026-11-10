import { http, HttpResponse } from 'msw';
import type { AnalysisResponse } from '../api/analysis';
import type {
  ApplicationResponse,
  ApplicationStatus,
  ChangeStatusRequest,
  CreateApplicationRequest,
  UpdateApplicationRequest,
} from '../api/applications';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../api/auth';
import type { OfferResponse, OfferSummary } from '../api/offers';

/** Prefijo de las rutas de la API en los tests (el cliente usa la ruta relativa /api/v1). */
export const API = '*/api/v1';

export const DEMO_EMAIL = 'demo@jobpilot.dev';
export const DEMO_PASSWORD = 'Demo1234!';

export const demoUser = {
  id: '0b6c3f0e-6d55-4a8e-9a54-6a1f4f6c2a11',
  email: DEMO_EMAIL,
  fullName: 'Lucía Demo',
};

export function authResponse(user = demoUser): AuthResponse {
  return { accessToken: 'test.jwt.token', tokenType: 'Bearer', expiresIn: 3600, user };
}

export function problem(status: number, slug: string, title: string, detail: string, extra: object = {}) {
  return HttpResponse.json(
    { type: `https://jobpilot.dev/problems/${slug}`, title, status, detail, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

export const handlers = [
  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as LoginRequest;
    if (body.email.toLowerCase() === DEMO_EMAIL && body.password === DEMO_PASSWORD) {
      return HttpResponse.json(authResponse());
    }
    return problem(401, 'invalid-credentials', 'Credenciales incorrectas', 'El email o la contraseña no son correctos.');
  }),
  http.post(`${API}/auth/register`, async ({ request }) => {
    const body = (await request.json()) as RegisterRequest;
    return HttpResponse.json(
      authResponse({ id: '5f0a2b7c-1d3e-4f5a-8b9c-0d1e2f3a4b5c', email: body.email.toLowerCase(), fullName: body.fullName }),
      { status: 201 },
    );
  }),
];

// ---------------------------------------------------------------------------
// Ofertas (JP-FE-03): las 3 del seed y la del Ejemplo 1 de la sección 4.
// ---------------------------------------------------------------------------

export const seedOffers: OfferSummary[] = [
  {
    id: '55555555-0000-4000-8000-000000000003',
    title: 'Desarrollador/a Frontend Senior (Angular)',
    company: 'Consultora Tecnológica Muestra',
    createdAt: '2026-11-07T10:00:00Z',
    extractionStatus: 'COMPLETADA',
    latestScore: 42,
    hasBlockingGap: true,
    applicationStatus: 'GUARDADA',
  },
  {
    id: '55555555-0000-4000-8000-000000000002',
    title: 'Desarrollador/a Full Stack',
    company: 'Plataformas Digitales Ejemplo',
    createdAt: '2026-10-28T10:00:00Z',
    extractionStatus: 'COMPLETADA',
    latestScore: 68,
    hasBlockingGap: false,
    applicationStatus: 'ENVIADA',
  },
  {
    id: '55555555-0000-4000-8000-000000000001',
    title: 'Desarrollador/a Java Junior',
    company: 'Innova Software Ficticio',
    createdAt: '2026-10-20T10:00:00Z',
    extractionStatus: 'COMPLETADA',
    latestScore: 85,
    hasBlockingGap: false,
    applicationStatus: 'ENTREVISTA',
  },
];

/** Texto del Ejemplo 1 (más de 200 caracteres). */
export const EXAMPLE_OFFER_TEXT =
  'Buscamos Desarrollador/a Java Junior para nuestro equipo de producto en Valencia (modelo híbrido, 2 días en ' +
  'oficina). Requisitos: experiencia de al menos 1 año con Java y Spring Boot, conocimientos de SQL y PostgreSQL, ' +
  'uso de Git. Se valorará: experiencia con React, Docker y nivel de inglés B2. Ofrecemos 24.000 - 28.000 € ' +
  'brutos anuales.';

export const EXAMPLE_OFFER_ID = '7c1e6a52-3b8f-4d0a-9a51-0f2c7f1d9b10';

/** Respuesta `201` del Ejemplo 1 de la sección 4. */
export function exampleOffer(overrides: Partial<OfferResponse> = {}): OfferResponse {
  return {
    id: EXAMPLE_OFFER_ID,
    title: 'Desarrollador/a Java Junior',
    company: null,
    location: 'Valencia',
    workMode: 'HIBRIDO',
    salary: { min: 24000, max: 28000, currency: 'EUR' },
    sourceUrl: 'https://empresa-ejemplo.com/empleo/java-junior',
    extractionStatus: 'COMPLETADA',
    requirements: [
      { id: 'a1', description: 'Al menos 1 año de experiencia con Java y Spring Boot', type: 'OBLIGATORIO', category: 'EXPERIENCIA' },
      { id: 'a2', description: 'Conocimientos de SQL y PostgreSQL', type: 'OBLIGATORIO', category: 'TECNICO' },
      { id: 'a3', description: 'Uso de Git', type: 'OBLIGATORIO', category: 'TECNICO' },
      { id: 'a4', description: 'Experiencia con React', type: 'DESEABLE', category: 'TECNICO' },
      { id: 'a5', description: 'Docker', type: 'DESEABLE', category: 'TECNICO' },
      { id: 'a6', description: 'Inglés B2', type: 'DESEABLE', category: 'IDIOMA' },
    ],
    createdAt: '2026-11-04T16:30:12Z',
    ...overrides,
  };
}

/** Oferta cuya extracción ha fallado: solo se conserva el texto (que la API no devuelve). */
export function failedOffer(overrides: Partial<OfferResponse> = {}): OfferResponse {
  return exampleOffer({
    title: null,
    company: null,
    location: null,
    workMode: 'DESCONOCIDO',
    salary: null,
    extractionStatus: 'FALLIDA',
    requirements: [],
    ...overrides,
  });
}

// ---------------------------------------------------------------------------
// Análisis de encaje (JP-FE-04): el Ejemplo 2 de la sección 4 y uno con requisito bloqueante.
// ---------------------------------------------------------------------------

/** Respuesta `201` del Ejemplo 2: obligatorios CUMPLE ×3, deseables PARCIAL / NO_CUMPLE / CUMPLE → 85. */
export function exampleAnalysis(overrides: Partial<AnalysisResponse> = {}): AnalysisResponse {
  return {
    id: 'e3b1f0c4-0000-4000-8000-000000000001',
    offerId: EXAMPLE_OFFER_ID,
    score: 85,
    hasBlockingGap: false,
    summary: 'Cumples todos los requisitos obligatorios. Tu principal margen de mejora está en Docker.',
    matches: [
      {
        requirementId: 'a1',
        description: 'Al menos 1 año de experiencia con Java y Spring Boot',
        type: 'OBLIGATORIO',
        result: 'CUMPLE',
        evidence: 'Prácticas de 12 meses como desarrollador Java (Spring Boot)',
      },
      {
        requirementId: 'a2',
        description: 'Conocimientos de SQL y PostgreSQL',
        type: 'OBLIGATORIO',
        result: 'CUMPLE',
        evidence: 'APIs REST sobre PostgreSQL en el puesto actual',
      },
      {
        requirementId: 'a3',
        description: 'Uso de Git',
        type: 'OBLIGATORIO',
        result: 'CUMPLE',
        evidence: 'Control de versiones con Git en todos los proyectos',
      },
      {
        requirementId: 'a4',
        description: 'Experiencia con React',
        type: 'DESEABLE',
        result: 'PARCIAL',
        evidence: 'Proyecto personal con React; sin experiencia profesional',
      },
      { requirementId: 'a5', description: 'Docker', type: 'DESEABLE', result: 'NO_CUMPLE', evidence: null },
      { requirementId: 'a6', description: 'Inglés B2', type: 'DESEABLE', result: 'CUMPLE', evidence: 'Inglés B2' },
    ],
    suggestions: [
      'Pon tu experiencia con Spring Boot en la primera línea del resumen.',
      'Menciona tu proyecto con React con un enlace al repositorio.',
      'Añade un Dockerfile a uno de tus proyectos públicos y menciónalo.',
    ],
    createdAt: '2026-11-04T16:31:02Z',
    ...overrides,
  };
}

/** Análisis con dos obligatorios NO_CUMPLE (como la oferta de 42 del seed). */
export function blockingAnalysis(): AnalysisResponse {
  const base = exampleAnalysis();
  return {
    ...base,
    id: 'e3b1f0c4-0000-4000-8000-000000000002',
    score: 42,
    hasBlockingGap: true,
    summary: 'No cumples dos requisitos obligatorios.',
    matches: base.matches.map((match) =>
      match.requirementId === 'a2' || match.requirementId === 'a3'
        ? { ...match, result: 'NO_CUMPLE', evidence: null }
        : match,
    ),
  };
}

export const offerHandlers = [
  http.get(`${API}/offers`, () => HttpResponse.json(seedOffers)),
  http.get(`${API}/offers/:offerId`, ({ params }) =>
    params.offerId === EXAMPLE_OFFER_ID
      ? HttpResponse.json(exampleOffer())
      : problem(404, 'not-found', 'Recurso no encontrado', 'La oferta no existe.'),
  ),
  // Por defecto la oferta aún no tiene análisis; el POST devuelve el Ejemplo 2.
  http.get(`${API}/offers/:offerId/analysis`, () =>
    problem(404, 'not-found', 'Recurso no encontrado', 'La oferta aún no tiene análisis.'),
  ),
  http.post(`${API}/offers/:offerId/analysis`, () => HttpResponse.json(exampleAnalysis(), { status: 201 })),
  http.post(`${API}/offers`, () =>
    HttpResponse.json(exampleOffer(), { status: 201, headers: { Location: `/api/v1/offers/${EXAMPLE_OFFER_ID}` } }),
  ),
];

// ---------------------------------------------------------------------------
// Tablero (JP-FE-06): candidaturas en un almacén en memoria que imita al backend (renumera las columnas y sube la
// `version` de las tarjetas que cambian), para comprobar que lo movido se mantiene al recargar.
// ---------------------------------------------------------------------------

/** "Hoy" en los tests del tablero (como el `Clock` del backend). */
export const BOARD_TODAY = '2026-11-10';

export function seedApplications(): ApplicationResponse[] {
  return [
    {
      id: 'aaaaaaaa-0000-4000-8000-000000000001',
      offerId: '55555555-0000-4000-8000-000000000003',
      offerTitle: 'Desarrollador/a Frontend Senior (Angular)',
      company: 'Consultora Tecnológica Muestra',
      score: 42,
      status: 'GUARDADA',
      boardPosition: 0,
      appliedAt: null,
      followUpDate: null,
      followUpOverdue: false,
      notes: null,
      version: 0,
    },
    {
      id: 'aaaaaaaa-0000-4000-8000-000000000002',
      offerId: '55555555-0000-4000-8000-000000000004',
      offerTitle: 'Backend Developer Kotlin',
      company: 'Startup Ficticia',
      score: null,
      status: 'GUARDADA',
      boardPosition: 1,
      appliedAt: null,
      followUpDate: null,
      followUpOverdue: false,
      notes: null,
      version: 2,
    },
    {
      id: 'aaaaaaaa-0000-4000-8000-000000000003',
      offerId: '55555555-0000-4000-8000-000000000002',
      offerTitle: 'Desarrollador/a Full Stack',
      company: 'Plataformas Digitales Ejemplo',
      score: 68,
      status: 'ENVIADA',
      boardPosition: 0,
      appliedAt: '2026-10-29',
      followUpDate: '2026-11-05',
      followUpOverdue: true,
      notes: 'Escribir a la recruiter.',
      version: 1,
    },
    {
      id: 'aaaaaaaa-0000-4000-8000-000000000004',
      offerId: '55555555-0000-4000-8000-000000000001',
      offerTitle: 'Desarrollador/a Java Junior',
      company: 'Innova Software Ficticio',
      score: 85,
      status: 'ENTREVISTA',
      boardPosition: 0,
      appliedAt: '2026-10-21',
      followUpDate: '2026-11-20',
      followUpOverdue: false,
      notes: null,
      version: 3,
    },
    {
      id: 'aaaaaaaa-0000-4000-8000-000000000005',
      offerId: '55555555-0000-4000-8000-000000000005',
      offerTitle: null,
      company: null,
      score: null,
      status: 'OFERTA',
      boardPosition: 0,
      appliedAt: '2026-09-01',
      followUpDate: '2026-10-01',
      followUpOverdue: false,
      notes: null,
      version: 4,
    },
  ];
}

let applicationStore: ApplicationResponse[] = seedApplications();

/** Lo llama `setup.ts` después de cada test. */
export function resetApplicationStore(cards: ApplicationResponse[] = seedApplications()): void {
  applicationStore = cards.map((card) => ({ ...card }));
}

export function storedApplications(): ApplicationResponse[] {
  return applicationStore.map((card) => ({ ...card }));
}

const BOARD_ORDER: ApplicationStatus[] = ['GUARDADA', 'ENVIADA', 'ENTREVISTA', 'OFERTA', 'DESCARTADA'];

function storedBoard(): ApplicationResponse[] {
  return [...applicationStore].sort(
    (a, b) => BOARD_ORDER.indexOf(a.status) - BOARD_ORDER.indexOf(b.status) || a.boardPosition - b.boardPosition,
  );
}

/** Como `BoardColumn.place` del backend: coloca la tarjeta en el índice pedido y renumera las columnas. */
function storePlace(id: string, status: ApplicationStatus, index: number): void {
  const moved = applicationStore.find((card) => card.id === id);
  if (!moved) return;
  const origin = moved.status;
  const column = storedBoard().filter((card) => card.status === status && card.id !== id);
  column.splice(Math.min(index, column.length), 0, moved);
  moved.status = status;
  const renumber = (cards: ApplicationResponse[]) =>
    cards.forEach((card, position) => {
      if (card.boardPosition !== position || card.id === id) card.version += 1;
      card.boardPosition = position;
    });
  renumber(column);
  if (origin !== status) renumber(storedBoard().filter((card) => card.status === origin));
}

export function applicationAlreadyExists() {
  return problem(
    409,
    'application-already-exists',
    'La oferta ya está en el tablero',
    'Esta oferta ya tiene una candidatura en el tablero.',
  );
}

function versionConflict() {
  return problem(409, 'version-conflict', 'La candidatura ha cambiado', 'Otra pestaña ha modificado esta candidatura.');
}

export const applicationHandlers = [
  http.get(`${API}/applications`, () => HttpResponse.json(storedBoard())),
  // Como el backend: al final de GUARDADA, y 409 application-already-exists si la oferta ya tiene candidatura.
  http.post(`${API}/applications`, async ({ request }) => {
    const { offerId } = (await request.json()) as CreateApplicationRequest;
    if (applicationStore.some((card) => card.offerId === offerId)) return applicationAlreadyExists();
    const offer = offerId === EXAMPLE_OFFER_ID ? exampleOffer() : null;
    const card: ApplicationResponse = {
      id: `bbbbbbbb-0000-4000-8000-${String(applicationStore.length + 1).padStart(12, '0')}`,
      offerId,
      offerTitle: offer?.title ?? null,
      company: offer?.company ?? null,
      score: null,
      status: 'GUARDADA',
      boardPosition: applicationStore.filter((stored) => stored.status === 'GUARDADA').length,
      appliedAt: null,
      followUpDate: null,
      followUpOverdue: false,
      notes: null,
      version: 0,
    };
    applicationStore.push(card);
    return HttpResponse.json({ ...card }, { status: 201 });
  }),
  http.patch(`${API}/applications/:applicationId/status`, async ({ params, request }) => {
    const body = (await request.json()) as ChangeStatusRequest;
    const card = applicationStore.find((stored) => stored.id === params.applicationId);
    if (!card) return problem(404, 'not-found', 'Recurso no encontrado', 'No existe la candidatura.');
    if (card.version !== body.version) return versionConflict();
    if (body.status === 'ENVIADA' && card.appliedAt === null) card.appliedAt = BOARD_TODAY;
    storePlace(card.id, body.status, body.boardPosition);
    return HttpResponse.json({ ...card });
  }),
  http.patch(`${API}/applications/:applicationId`, async ({ params, request }) => {
    const body = (await request.json()) as UpdateApplicationRequest;
    const card = applicationStore.find((stored) => stored.id === params.applicationId);
    if (!card) return problem(404, 'not-found', 'Recurso no encontrado', 'No existe la candidatura.');
    if (card.version !== body.version) return versionConflict();
    if (body.notes !== undefined) card.notes = body.notes;
    if (body.appliedAt !== undefined) card.appliedAt = body.appliedAt;
    if (body.followUpDate !== undefined) {
      card.followUpDate = body.followUpDate;
      card.followUpOverdue = body.followUpDate !== null && body.followUpDate < BOARD_TODAY;
    }
    if (body.boardPosition !== undefined) storePlace(card.id, card.status, body.boardPosition);
    else card.version += 1;
    return HttpResponse.json({ ...card });
  }),
];

/** La candidatura n.º `n` (1–5) de {@link seedApplications}: 1 Angular y 2 Kotlin (Guardada), 3 Full Stack (Enviada), 4 Java (Entrevista), 5 sin título (Oferta). */
export function seedApplication(n: 1 | 2 | 3 | 4 | 5): ApplicationResponse {
  const card = seedApplications()[n - 1];
  if (!card) throw new Error(`No hay candidatura ${n} en el seed`);
  return card;
}

/** El elemento `index` de una lista que el test sabe que existe (falla con un mensaje claro si no). */
export function nth<T>(items: readonly T[], index: number): T {
  const item = items[index];
  if (item === undefined) throw new Error(`No hay elemento ${index} (la lista tiene ${items.length})`);
  return item;
}
