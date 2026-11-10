import type { MatchResult, RequirementMatch } from '../../api/analysis';
import type { RequirementType } from '../../api/offers';

/** Resultado por requisito: el texto acompaña siempre al icono (el icono es decorativo). */
export const MATCH_RESULT_LABELS: Record<MatchResult, { label: string; icon: string; className: string }> = {
  CUMPLE: { label: 'Cumple', icon: '✅', className: 'bg-green-100 text-green-800' },
  PARCIAL: { label: 'Parcial', icon: '⚠️', className: 'bg-amber-100 text-amber-900' },
  NO_CUMPLE: { label: 'No cumple', icon: '❌', className: 'bg-red-100 text-red-800' },
};

export const REQUIREMENT_TYPE_LABELS: Record<RequirementType, string> = {
  OBLIGATORIO: 'Obligatorio',
  DESEABLE: 'Deseable',
};

/** Requisito bloqueante (sección 1.2): obligatorio y `NO_CUMPLE`. */
export function isBlocking(match: RequirementMatch): boolean {
  return match.type === 'OBLIGATORIO' && match.result === 'NO_CUMPLE';
}
