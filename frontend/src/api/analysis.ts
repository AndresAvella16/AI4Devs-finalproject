import { apiFetch } from './client';
import type { RequirementType } from './offers';

export type MatchResult = 'CUMPLE' | 'PARCIAL' | 'NO_CUMPLE';

/** `RequirementMatch` (sección 4): resultado de un requisito, en el orden de la oferta. */
export interface RequirementMatch {
  requirementId: string;
  description: string;
  type: RequirementType;
  result: MatchResult;
  evidence: string | null;
}

/** `AnalysisResponse` (sección 4). `score` lo calcula el backend con `MatchScoreCalculator`; `suggestions` trae 3–5. */
export interface AnalysisResponse {
  id: string;
  offerId: string;
  score: number;
  hasBlockingGap: boolean;
  summary: string;
  matches: RequirementMatch[];
  suggestions: string[];
  createdAt: string;
}

/** `GET /offers/{offerId}/analysis`: el análisis más reciente. 404 si la oferta aún no tiene ninguno. */
export function getLatestAnalysis(offerId: string, signal?: AbortSignal): Promise<AnalysisResponse> {
  return apiFetch<AnalysisResponse>(`/offers/${encodeURIComponent(offerId)}/analysis`, { signal });
}

/** `POST /offers/{offerId}/analysis`: calcula un análisis nuevo (se guarda un histórico). Consume cupo de IA. */
export function createAnalysis(offerId: string): Promise<AnalysisResponse> {
  return apiFetch<AnalysisResponse>(`/offers/${encodeURIComponent(offerId)}/analysis`, { method: 'POST' });
}
