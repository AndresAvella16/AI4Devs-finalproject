import type { ApplicationStatus } from './applications';
import { apiFetch } from './client';

export type WorkMode = 'REMOTO' | 'HIBRIDO' | 'PRESENCIAL' | 'DESCONOCIDO';
export type ExtractionStatus = 'PENDIENTE' | 'COMPLETADA' | 'FALLIDA';
export type RequirementType = 'OBLIGATORIO' | 'DESEABLE';
export type RequirementCategory = 'TECNICO' | 'IDIOMA' | 'EXPERIENCIA' | 'FORMACION' | 'OTRO';

/** `CreateOfferRequest` (sección 4). `sourceUrl` vacía equivale a `null`. */
export interface CreateOfferRequest {
  rawText: string;
  sourceUrl: string | null;
}

/** Puede faltar un extremo, pero no los dos; la moneda siempre tiene valor. */
export interface Salary {
  min: number | null;
  max: number | null;
  currency: string;
}

/** `Requirement` (sección 4). */
export interface Requirement {
  id: string;
  description: string;
  type: RequirementType;
  category: RequirementCategory;
}

/**
 * `OfferResponse` (sección 4). Con la extracción PENDIENTE o FALLIDA, `title`, `company`, `location` y `salary`
 * son `null`, `workMode` es `DESCONOCIDO` y no hay requisitos.
 */
export interface OfferResponse {
  id: string;
  title: string | null;
  company: string | null;
  location: string | null;
  workMode: WorkMode;
  salary: Salary | null;
  sourceUrl: string | null;
  extractionStatus: ExtractionStatus;
  requirements: Requirement[];
  createdAt: string;
}

/** `OfferSummary` (sección 4): elemento del dashboard. */
export interface OfferSummary {
  id: string;
  title: string | null;
  company: string | null;
  createdAt: string;
  extractionStatus: ExtractionStatus;
  latestScore: number | null;
  hasBlockingGap: boolean | null;
  applicationStatus: ApplicationStatus | null;
}

/** `GET /offers`: ofertas del usuario, de la más reciente a la más antigua. */
export function listOffers(signal?: AbortSignal): Promise<OfferSummary[]> {
  return apiFetch<OfferSummary[]>('/offers', { signal });
}

/** `GET /offers/{offerId}`. */
export function getOffer(offerId: string, signal?: AbortSignal): Promise<OfferResponse> {
  return apiFetch<OfferResponse>(`/offers/${encodeURIComponent(offerId)}`, { signal });
}

/** `POST /offers`: 201 aunque la IA falle (la oferta queda FALLIDA). */
export function createOffer(request: CreateOfferRequest): Promise<OfferResponse> {
  return apiFetch<OfferResponse>('/offers', { method: 'POST', body: request });
}

/** `POST /offers/{offerId}/extraction`: reintenta la extracción de una oferta PENDIENTE o FALLIDA. */
export function retryExtraction(offerId: string): Promise<OfferResponse> {
  return apiFetch<OfferResponse>(`/offers/${encodeURIComponent(offerId)}/extraction`, { method: 'POST' });
}
