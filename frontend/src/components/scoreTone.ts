export type ScoreTone = 'alto' | 'medio' | 'bajo' | 'sin-analizar';

/** Rangos de la sección 1.3: rojo < 50, ámbar 50–74, verde ≥ 75. `null` = la oferta aún no tiene análisis. */
export function scoreTone(score: number | null): ScoreTone {
  if (score === null) return 'sin-analizar';
  if (score >= 75) return 'alto';
  if (score >= 50) return 'medio';
  return 'bajo';
}
