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
  const response = await send(path, options, 'application/json, application/problem+json');
  const text = await response.text();
  return { status: response.status, data: (text === '' ? undefined : JSON.parse(text)) as T };
}

/** Fichero descargado de la API: su contenido y el nombre que propone el servidor en `Content-Disposition`. */
export interface DownloadedFile {
  blob: Blob;
  fileName: string | null;
}

/**
 * `GET` de un fichero (p. ej. el PDF de un documento) con el JWT, que un enlace normal no enviaría. Pide el tipo
 * indicado (y `problem+json` para los errores) y trata los errores igual que {@link apiFetch}.
 */
export async function apiDownload(path: string, accept: string, signal?: AbortSignal): Promise<DownloadedFile> {
  const response = await send(path, { signal }, `${accept}, application/problem+json`);
  return { blob: await response.blob(), fileName: readFileName(response.headers.get('Content-Disposition')) };
}

/** Hace la petición y, si no es correcta, lanza el {@link ApiError} (y cierra la sesión ante un 401). */
async function send(path: string, options: ApiRequestOptions, accept: string): Promise<Response> {
  const { method = 'GET', body, auth = true, signal } = options;
  const multipart = body instanceof FormData;

  const headers = new Headers({ Accept: accept });
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
  return response;
}

/** `filename*=UTF-8''...` (RFC 5987), si está; si no, `filename="..."`. `null` si no hay ninguno. */
function readFileName(contentDisposition: string | null): string | null {
  if (contentDisposition === null) return null;
  const extended = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(contentDisposition);
  if (extended?.[1]) {
    try {
      return decodeURIComponent(extended[1].trim());
    } catch {
      // Codificación rota: se usa el filename simple.
    }
  }
  const plain = /filename\s*=\s*(?:"([^"]*)"|([^;]+))/i.exec(contentDisposition);
  const name = (plain?.[1] ?? plain?.[2])?.trim();
  return name ? name : null;
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
