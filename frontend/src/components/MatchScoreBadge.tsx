import { scoreTone, type ScoreTone } from './scoreTone';

const TONE_CLASSES: Record<ScoreTone, string> = {
  alto: 'border-green-300 bg-green-100 text-green-800',
  medio: 'border-amber-300 bg-amber-100 text-amber-900',
  bajo: 'border-red-300 bg-red-100 text-red-800',
  'sin-analizar': 'border-slate-300 bg-slate-100 text-slate-600',
};

interface MatchScoreBadgeProps {
  score: number | null;
}

/** Puntuación de encaje (0–100) con su color. El número va siempre en texto: el color no es la única pista. */
export function MatchScoreBadge({ score }: MatchScoreBadgeProps) {
  const tone = scoreTone(score);

  return (
    <span
      data-testid="match-score-badge"
      data-tone={tone}
      className={`inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 text-sm font-semibold ${TONE_CLASSES[tone]}`}
    >
      {score === null ? (
        'Sin analizar'
      ) : (
        <>
          <span className="sr-only">Encaje: </span>
          {score}
          <span className="sr-only"> de 100</span>
        </>
      )}
    </span>
  );
}
