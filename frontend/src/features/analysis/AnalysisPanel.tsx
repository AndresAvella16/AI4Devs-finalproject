import { useNavigate } from 'react-router';
import type { AnalysisResponse } from '../../api/analysis';
import { ApiError } from '../../api/ApiError';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { toGeneralError } from '../../components/serverErrors';
import { formatDate } from '../offers/labels';
import { formatWait, isBlocking } from './labels';
import { RequirementMatchTable } from './RequirementMatchTable';
import { ScoreRing } from './ScoreRing';
import { useCreateAnalysis, useLatestAnalysis } from './useAnalysis';

/** Ruta a la que se lleva al usuario sin perfil (409 `profile-required`). JP-FE-02 la convierte en el onboarding. */
export const PROFILE_PATH = '/perfil';

const HELP_ID = 'analysis-help';

/**
 * Panel de análisis de encaje (HU-03) en el detalle de una oferta COMPLETADA: último análisis, botón para calcularlo
 * (o recalcularlo) y errores de la IA. La puntuación la calcula el backend; aquí solo se pinta.
 */
export function AnalysisPanel({ offerId }: { offerId: string }) {
  const navigate = useNavigate();
  const latest = useLatestAnalysis(offerId);
  const create = useCreateAnalysis(offerId);

  const analysis = latest.data ?? null;
  const calculate = () =>
    create.mutate(undefined, {
      onError: (error) => {
        if (error instanceof ApiError && error.slug === 'profile-required') void navigate(PROFILE_PATH);
      },
    });
  const showError = create.isError && !(create.error instanceof ApiError && create.error.slug === 'profile-required');

  return (
    <section
      aria-labelledby="analysis-heading"
      data-testid="analysis-panel"
      className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="analysis-heading" className="text-lg font-semibold text-slate-900">
            Análisis de encaje
          </h2>
          {analysis && (
            <p className="text-xs text-slate-500">
              Calculado el <time dateTime={analysis.createdAt}>{formatDate(analysis.createdAt)}</time>
            </p>
          )}
        </div>
        {latest.isSuccess && (
          <div className="flex flex-col items-end gap-1">
            <Button
              data-testid="calculate-analysis-button"
              variant={analysis ? 'secondary' : 'primary'}
              onClick={calculate}
              disabled={create.isPending}
              aria-describedby={HELP_ID}
            >
              {create.isPending ? 'Calculando…' : analysis ? 'Volver a calcular' : 'Calcular encaje'}
            </Button>
            <p id={HELP_ID} className="text-xs text-slate-500">
              Cada cálculo consume una consulta de IA (30 por hora).
            </p>
          </div>
        )}
      </div>

      {latest.isPending && (
        <div data-testid="analysis-loading" aria-busy="true" className="flex items-center gap-4">
          <span className="sr-only" role="status">
            Cargando el análisis…
          </span>
          <div className="size-36 animate-pulse rounded-full bg-slate-200" />
          <div className="h-16 flex-1 animate-pulse rounded bg-slate-200" />
        </div>
      )}

      {latest.isError && (
        <div className="flex flex-col items-start gap-3">
          <Alert title="No se ha podido cargar el análisis" testId="analysis-load-error">
            {toGeneralError(latest.error).detail}
          </Alert>
          <Button variant="secondary" onClick={() => void latest.refetch()} disabled={latest.isFetching}>
            Reintentar
          </Button>
        </div>
      )}

      {create.isPending && (
        <p role="status" data-testid="analysis-calculating" className="text-sm font-medium text-indigo-700">
          Calculando tu encaje…
        </p>
      )}

      {showError && <AnalysisError error={create.error} />}

      {latest.isSuccess &&
        (analysis ? (
          <AnalysisResult analysis={analysis} />
        ) : (
          !create.isPending && (
            <div data-testid="analysis-empty" className="text-sm text-slate-600">
              <p className="font-medium text-slate-800">Aún no has calculado tu encaje con esta oferta.</p>
              <p>Compararemos tu perfil con cada requisito y te daremos una puntuación de 0 a 100 y sugerencias.</p>
            </div>
          )
        ))}
    </section>
  );
}

function AnalysisError({ error }: { error: ApiError | Error | null }) {
  const { title, detail } = toGeneralError(error);
  const retryAfter = error instanceof ApiError ? error.retryAfter : null;

  return (
    <Alert title={title} testId="analysis-error">
      {detail && <p>{detail}</p>}
      {retryAfter !== null && retryAfter > 0 && <p>Vuelve a intentarlo dentro de {formatWait(retryAfter)}.</p>}
    </Alert>
  );
}

function AnalysisResult({ analysis }: { analysis: AnalysisResponse }) {
  const blocking = analysis.matches.filter(isBlocking);

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
        <ScoreRing score={analysis.score} />
        <p data-testid="analysis-summary" className="text-slate-800">
          {analysis.summary}
        </p>
      </div>

      {analysis.hasBlockingGap && (
        <div data-testid="blocking-gap-alert" className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          <p className="font-semibold">Hay requisitos obligatorios que no cumples</p>
          {blocking.length > 0 && (
            <ul className="mt-1 list-disc pl-5">
              {blocking.map((match) => (
                <li key={match.requirementId}>{match.description}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <RequirementMatchTable matches={analysis.matches} />

      <div data-testid="analysis-suggestions">
        <h3 className="text-base font-semibold text-slate-900">Sugerencias</h3>
        <ul className="mt-2 flex list-disc flex-col gap-1 pl-5 text-sm text-slate-800">
          {analysis.suggestions.map((suggestion) => (
            <li key={suggestion}>{suggestion}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}
