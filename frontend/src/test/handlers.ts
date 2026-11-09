import { http, HttpResponse } from 'msw';
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

export const offerHandlers = [
  http.get(`${API}/offers`, () => HttpResponse.json(seedOffers)),
  http.get(`${API}/offers/:offerId`, ({ params }) =>
    params.offerId === EXAMPLE_OFFER_ID
      ? HttpResponse.json(exampleOffer())
      : problem(404, 'not-found', 'Recurso no encontrado', 'La oferta no existe.'),
  ),
  http.post(`${API}/offers`, () =>
    HttpResponse.json(exampleOffer(), { status: 201, headers: { Location: `/api/v1/offers/${EXAMPLE_OFFER_ID}` } }),
  ),
];
