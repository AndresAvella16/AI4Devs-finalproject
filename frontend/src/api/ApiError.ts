/** Error de un campo en un `400 validation-error` (sección 4). */
export interface FieldError {
  field: string;
  message: string;
}

/** Cuerpo `application/problem+json` (RFC 9457) que devuelve la API en todos sus errores. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: FieldError[];
}

const PROBLEM_TYPE_PREFIX = 'https://jobpilot.dev/problems/';

/**
 * Error de una llamada a la API. `status` es 0 si no se ha podido conectar con el servidor.
 * `slug` es la parte final del `type` (p. ej. `invalid-credentials`), o `null` si no es un tipo de JobPilot.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly type: string | null;
  readonly title: string;
  readonly detail: string | null;
  readonly errors: FieldError[];
  readonly retryAfter: number | null;

  constructor(status: number, problem: ProblemDetail = {}, retryAfter: number | null = null) {
    const title = problem.title ?? defaultTitle(status);
    super(problem.detail ?? title);
    this.name = 'ApiError';
    this.status = status;
    this.type = problem.type ?? null;
    this.title = title;
    this.detail = problem.detail ?? null;
    this.errors = Array.isArray(problem.errors) ? problem.errors : [];
    this.retryAfter = retryAfter;
  }

  get slug(): string | null {
    return this.type?.startsWith(PROBLEM_TYPE_PREFIX) ? this.type.slice(PROBLEM_TYPE_PREFIX.length) : null;
  }
}

function defaultTitle(status: number): string {
  if (status === 0) return 'No se ha podido conectar con el servidor';
  if (status >= 500) return 'Error del servidor';
  return 'Error en la petición';
}
