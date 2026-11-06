import { http, HttpResponse } from 'msw';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../api/auth';

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
