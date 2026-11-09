import { scoreTone, type ScoreTone } from '../../components/scoreTone';

type AnalyzedTone = Exclude<ScoreTone, 'sin-analizar'>;

const TONES: Record<AnalyzedTone, { stroke: string; text: string; label: string }> = {
  alto: { stroke: 'stroke-green-600', text: 'text-green-800', label: 'Encaje alto' },
  medio: { stroke: 'stroke-amber-500', text: 'text-amber-900', label: 'Encaje medio' },
  bajo: { stroke: 'stroke-red-600', text: 'text-red-800', label: 'Encaje bajo' },
};

const RADIUS = 52;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** Puntuación de encaje (0–100) en un anillo con el color de la sección 1.3. El número y el nivel van en texto. */
export function ScoreRing({ score }: { score: number }) {
  const tone = scoreTone(score) as AnalyzedTone;
  const { stroke, text, label } = TONES[tone];

  return (
    <div
      role="img"
      aria-label={`Encaje: ${score} de 100`}
      data-testid="analysis-score"
      data-tone={tone}
      className="relative flex size-36 shrink-0 items-center justify-center"
    >
      <svg viewBox="0 0 120 120" className="absolute inset-0 -rotate-90" aria-hidden="true">
        <circle cx="60" cy="60" r={RADIUS} fill="none" strokeWidth="12" className="stroke-slate-200" />
        <circle
          data-testid="analysis-score-arc"
          cx="60"
          cy="60"
          r={RADIUS}
          fill="none"
          strokeWidth="12"
          strokeLinecap="round"
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={CIRCUMFERENCE * (1 - score / 100)}
          className={stroke}
        />
      </svg>
      <div className={`flex flex-col items-center ${text}`} aria-hidden="true">
        <span className="text-4xl font-bold leading-none">{score}</span>
        <span className="mt-1 text-xs font-medium">{label}</span>
      </div>
    </div>
  );
}
