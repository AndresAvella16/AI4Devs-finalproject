import { apiFetch } from './client';

/** Estado de una candidatura en el tablero (sección 3, `applications.status`), en el orden de las columnas. */
export type ApplicationStatus = 'GUARDADA' | 'ENVIADA' | 'ENTREVISTA' | 'OFERTA' | 'DESCARTADA';

export const APPLICATION_STATUSES: readonly ApplicationStatus[] = [
  'GUARDADA',
  'ENVIADA',
  'ENTREVISTA',
  'OFERTA',
  'DESCARTADA',
];

/** `ApplicationResponse` (sección 4): una tarjeta del tablero. Las fechas son `YYYY-MM-DD`. */
export interface ApplicationResponse {
  id: string;
  offerId: string;
  offerTitle: string | null;
  company: string | null;
  score: number | null;
  status: ApplicationStatus;
  boardPosition: number;
  appliedAt: string | null;
  followUpDate: string | null;
  followUpOverdue: boolean;
  notes: string | null;
  version: number;
}

/** `ChangeStatusRequest` (sección 4): cambio de columna. `boardPosition` es el índice en la columna de destino. */
export interface ChangeStatusRequest {
  status: ApplicationStatus;
  boardPosition: number;
  version: number;
}

/**
 * `UpdateApplicationRequest` (sección 4). Solo se modifican los campos enviados; `null` borra el valor.
 * Con `boardPosition` reordena la tarjeta dentro de su columna.
 */
export interface UpdateApplicationRequest {
  notes?: string | null;
  appliedAt?: string | null;
  followUpDate?: string | null;
  boardPosition?: number;
  version: number;
}

/** `CreateApplicationRequest` (sección 4). */
export interface CreateApplicationRequest {
  offerId: string;
}

/** `POST /applications`: guarda la oferta en el tablero (al final de GUARDADA). 409 `application-already-exists`. */
export function createApplication(request: CreateApplicationRequest): Promise<ApplicationResponse> {
  return apiFetch<ApplicationResponse>('/applications', { method: 'POST', body: request });
}

/** `GET /applications`: el tablero, en el orden de las columnas y, dentro de cada una, por `boardPosition`. */
export function listApplications(signal?: AbortSignal): Promise<ApplicationResponse[]> {
  return apiFetch<ApplicationResponse[]>('/applications', { signal });
}

/** `PATCH /applications/{applicationId}/status`: mueve la tarjeta a otra columna (queda en el historial). */
export function changeApplicationStatus(applicationId: string, request: ChangeStatusRequest): Promise<ApplicationResponse> {
  return apiFetch<ApplicationResponse>(`/applications/${encodeURIComponent(applicationId)}/status`, {
    method: 'PATCH',
    body: request,
  });
}

/** `PATCH /applications/{applicationId}`: notas, fechas u orden dentro de la columna (no toca el historial). */
export function updateApplication(applicationId: string, request: UpdateApplicationRequest): Promise<ApplicationResponse> {
  return apiFetch<ApplicationResponse>(`/applications/${encodeURIComponent(applicationId)}`, {
    method: 'PATCH',
    body: request,
  });
}
