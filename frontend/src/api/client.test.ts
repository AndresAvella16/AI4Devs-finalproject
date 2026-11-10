import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { API, authResponse, problem } from '../test/handlers';
import { server } from '../test/server';
import { ApiError } from './ApiError';
import { apiFetch, apiRequest, setUnauthorizedHandler } from './client';
import { loadSession, saveSession } from './session';

afterEach(() => setUnauthorizedHandler(null));

function captureRequests() {
  const seen: { authorization: string | null; url: string }[] = [];
  server.use(
    http.get(`${API}/ping`, ({ request }) => {
      seen.push({ authorization: request.headers.get('Authorization'), url: request.url });
      return HttpResponse.json({ ok: true });
    }),
  );
  return seen;
}

describe('apiFetch', () => {
  it('llama a la API bajo /api/v1 y devuelve el JSON', async () => {
    const seen = captureRequests();

    await expect(apiFetch<{ ok: boolean }>('/ping')).resolves.toEqual({ ok: true });
    expect(seen.map((s) => new URL(s.url).pathname)).toEqual(['/api/v1/ping']);
  });

  it('añade Authorization: Bearer cuando hay sesión', async () => {
    saveSession(authResponse());
    const seen = captureRequests();

    await apiFetch('/ping');

    expect(seen.map((s) => s.authorization)).toEqual(['Bearer test.jwt.token']);
  });

  it('no añade Authorization sin sesión ni en las rutas públicas', async () => {
    const seen = captureRequests();
    await apiFetch('/ping');
    saveSession(authResponse());
    await apiFetch('/ping', { auth: false });

    expect(seen.map((s) => s.authorization)).toEqual([null, null]);
  });

  it('envía el cuerpo como JSON', async () => {
    let received: unknown;
    let contentType: string | null = null;
    server.use(
      http.post(`${API}/echo`, async ({ request }) => {
        contentType = request.headers.get('Content-Type');
        received = await request.json();
        return HttpResponse.json({}, { status: 201 });
      }),
    );

    await apiFetch('/echo', { method: 'POST', body: { a: 1 } });

    expect(contentType).toBe('application/json');
    expect(received).toEqual({ a: 1 });
  });

  it('envía un FormData como multipart, sin Content-Type JSON y con el token (subida del CV)', async () => {
    saveSession(authResponse());
    let contentType: string | null = null;
    let authorization: string | null = null;
    let payload = '';
    server.use(
      http.post(`${API}/upload`, async ({ request }) => {
        contentType = request.headers.get('Content-Type');
        authorization = request.headers.get('Authorization');
        payload = await request.text();
        return HttpResponse.json({ ok: true });
      }),
    );
    const body = new FormData();
    body.append('file', new File(['%PDF-1.4'], 'cv.pdf', { type: 'application/pdf' }));

    await apiFetch('/upload', { method: 'POST', body });

    expect(contentType).toMatch(/^multipart\/form-data; boundary=/);
    expect(authorization).toBe('Bearer test.jwt.token');
    // El nombre y el contenido no se comprueban: el puente de Vitest entre el File de jsdom y el fetch de Node los
    // pierde. La subida real la cubre el E2E.
    expect(payload).toContain('name="file"');
    expect(payload).toContain('Content-Type: application/pdf');
  });

  it('apiRequest devuelve también el código de estado (201 frente a 200)', async () => {
    server.use(http.put(`${API}/echo`, () => HttpResponse.json({ id: 'x' }, { status: 201 })));

    await expect(apiRequest('/echo', { method: 'PUT', body: {} })).resolves.toEqual({ status: 201, data: { id: 'x' } });
  });

  it('convierte un problem+json en ApiError con sus campos y Retry-After', async () => {
    server.use(
      http.get(`${API}/ping`, () =>
        HttpResponse.json(
          {
            type: 'https://jobpilot.dev/problems/validation-error',
            title: 'Datos de entrada no válidos',
            status: 400,
            detail: 'Revisa los campos marcados.',
            errors: [{ field: 'email', message: 'formato no válido' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '10' } },
        ),
      ),
    );

    const error = await apiFetch('/ping').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.slug).toBe('validation-error');
    expect(apiError.title).toBe('Datos de entrada no válidos');
    expect(apiError.detail).toBe('Revisa los campos marcados.');
    expect(apiError.errors).toEqual([{ field: 'email', message: 'formato no válido' }]);
    expect(apiError.retryAfter).toBe(10);
  });

  it('un error sin cuerpo JSON se convierte en ApiError genérico', async () => {
    server.use(http.get(`${API}/ping`, () => new HttpResponse('Bad gateway', { status: 502 })));

    const error = (await apiFetch('/ping').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.slug).toBeNull();
    expect(error.errors).toEqual([]);
  });

  it('un 401 en un endpoint protegido cierra la sesión y avisa', async () => {
    saveSession(authResponse());
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    server.use(http.get(`${API}/ping`, () => problem(401, 'unauthorized', 'No autenticado', 'Token caducado.')));

    await expect(apiFetch('/ping')).rejects.toMatchObject({ status: 401 });

    expect(onUnauthorized).toHaveBeenCalledOnce();
    expect(loadSession()).toBeNull();
  });

  it('un 401 en una ruta pública (login) no cierra la sesión', async () => {
    saveSession(authResponse());
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    server.use(
      http.get(`${API}/ping`, () => problem(401, 'invalid-credentials', 'Credenciales incorrectas', 'x')),
    );

    await expect(apiFetch('/ping', { auth: false })).rejects.toMatchObject({ status: 401 });

    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(loadSession()).not.toBeNull();
  });

  it('un fallo de red se convierte en ApiError con status 0', async () => {
    server.use(http.get(`${API}/ping`, () => HttpResponse.error()));

    await expect(apiFetch('/ping')).rejects.toMatchObject({
      status: 0,
      title: 'No se ha podido conectar con el servidor',
    });
  });

  it('una respuesta 204 devuelve undefined', async () => {
    server.use(http.get(`${API}/ping`, () => new HttpResponse(null, { status: 204 })));

    await expect(apiFetch('/ping')).resolves.toBeUndefined();
  });
});
