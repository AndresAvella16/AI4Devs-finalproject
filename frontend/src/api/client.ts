import { ApiError, type ProblemDetail } from './ApiError';
import { API_BASE_URL } from './config';
import { clearSession, loadSession } from './session';

export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Un `FormData` se envía como multipart (el navegador pone el `boundary`); cualquier otro valor, como JSON. */
  body?: unknown;
  /** `false` en las rutas públicas (login, registro): no se envía el token y un 401 no cierra la sesión. */
  auth?: boolean;
  signal?: AbortSignal;
}

let unauthorizedHandler: (() => void) | null = null;

/** Lo registra `AuthProvider` para enterarse de que el backend ha rechazado el token. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

/** Respuesta correcta de la API con su código de estado (p. ej. para distinguir 201 de 200 en `PUT /profile`). */
export interface ApiResponse<T> {
  status: number;
  data: T;
}

/**
 * Único punto de salida HTTP de la SPA. Añade el JWT, serializa el cuerpo y convierte cualquier error
 * (`application/problem+json`, otro cuerpo o fallo de red) en un {@link ApiError}.
 */
export async function apiFetch<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  return (await apiRequest<T>(path, options)).data;
}

/** Como {@link apiFetch}, pero devuelve también el código de estado. */
export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<ApiResponse<T>> {
  const { method = 'GET', body, auth = true, signal } = options;
  const multipart = body instanceof FormData;

  const headers = new Headers({ Accept: 'application/json, application/problem+json' });
  if (body !== undefined && !multipart) headers.set('Content-Type', 'application/json');
  const token = auth ? loadSession()?.accessToken : undefined;
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(toUrl(path), {
      method,
      headers,
      body: multipart ? body : body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0);
  }

  if (!response.ok) {
    const apiError = new ApiError(response.status, await readProblem(response), readRetryAfter(response));
    if (response.status === 401 && auth) {
      clearSession();
      unauthorizedHandler?.();
    }
    throw apiError;
  }

  const text = await response.text();
  return { status: response.status, data: (text === '' ? undefined : JSON.parse(text)) as T };
}

function toUrl(path: string): string {
  // URL absoluta a partir del origen actual: la base por defecto es relativa (/api/v1).
  return new URL(`${API_BASE_URL}${path}`, window.location.origin).toString();
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  if (!(response.headers.get('Content-Type') ?? '').includes('json')) return {};
  try {
    const parsed: unknown = await response.json();
    return typeof parsed === 'object' && parsed !== null ? (parsed as ProblemDetail) : {};
  } catch {
    return {};
  }
}

function readRetryAfter(response: Response): number | null {
  const value = response.headers.get('Retry-After');
  if (value === null) return null;
  const seconds = Number(value);
  return Number.isFinite(seconds) ? seconds : null;
}
