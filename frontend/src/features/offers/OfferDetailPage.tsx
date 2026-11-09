import { Link, useParams } from 'react-router';
import { ApiError } from '../../api/ApiError';
import type { OfferResponse, Requirement, RequirementType } from '../../api/offers';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { toGeneralError } from '../../components/serverErrors';
import { CATEGORY_LABELS, formatDate, formatSalary, UNTITLED_OFFER, WORK_MODE_LABELS } from './labels';
import { useOffer, useRetryExtraction } from './useOffers';

const backLink = 'text-sm font-medium text-indigo-700 underline-offset-2 hover:underline';

/**
 * Detalle básico de una oferta (`/ofertas/:offerId`): cabecera, requisitos agrupados y reintento de la extracción.
 * JP-FE-04 le añade el panel de análisis.
 */
export function OfferDetailPage() {
  const { offerId = '' } = useParams();
  const offer = useOffer(offerId);

  if (offer.isPending) {
    return (
      <div data-testid="offer-loading" aria-busy="true" className="flex flex-col gap-3">
        <span className="sr-only" role="status">
          Cargando la oferta…
        </span>
        <div className="h-8 w-2/3 animate-pulse rounded bg-slate-200" />
        <div className="h-40 animate-pulse rounded-lg bg-slate-200" />
      </div>
    );
  }

  if (offer.isError) {
    if (offer.error instanceof ApiError && offer.error.status === 404) {
      return (
        <section data-testid="offer-not-found" className="flex flex-col items-start gap-3">
          <h1 className="text-2xl font-bold text-slate-900">Oferta no encontrada</h1>
          <p className="text-slate-600">La oferta no existe o no es tuya.</p>
          <Link to="/" className={backLink}>
            Volver a tus ofertas
          </Link>
        </section>
      );
    }
    return (
      <section data-testid="offer-error" className="flex flex-col items-start gap-3">
        <Alert title="No se ha podido cargar la oferta">{toGeneralError(offer.error).detail}</Alert>
        <Button variant="secondary" onClick={() => void offer.refetch()} disabled={offer.isFetching}>
          Reintentar
        </Button>
      </section>
    );
  }

  return <OfferDetail offer={offer.data} />;
}

function OfferDetail({ offer }: { offer: OfferResponse }) {
  const completed = offer.extractionStatus === 'COMPLETADA';

  return (
    <article className="flex flex-col gap-6">
      <div>
        <Link to="/" className={backLink}>
          Volver a tus ofertas
        </Link>
      </div>

      <header data-testid="offer-header" className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h1 data-testid="offer-title" className="text-2xl font-bold text-slate-900">
          {offer.title ?? UNTITLED_OFFER}
        </h1>
        {completed && (
          <dl className="grid grid-cols-1 gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
            <Detail term="Empresa" value={offer.company ?? 'Empresa no indicada'} />
            <Detail term="Ubicación" value={offer.location ?? 'Ubicación no indicada'} />
            <Detail term="Modalidad" value={WORK_MODE_LABELS[offer.workMode]} />
            {offer.salary && <Detail term="Salario" value={formatSalary(offer.salary)} testId="offer-salary" />}
          </dl>
        )}
        <p className="text-xs text-slate-500">
          Importada el <time dateTime={offer.createdAt}>{formatDate(offer.createdAt)}</time>
          {isHttpUrl(offer.sourceUrl) && (
            <>
              {' · '}
              <a
                href={offer.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="font-medium text-indigo-700 underline-offset-2 hover:underline"
              >
                Ver la oferta original<span className="sr-only"> (se abre en otra pestaña)</span>
              </a>
            </>
          )}
        </p>
      </header>

      {completed ? (
        <div className="grid gap-4 md:grid-cols-2">
          <RequirementGroup title="Obligatorios" type="OBLIGATORIO" requirements={offer.requirements} />
          <RequirementGroup title="Deseables" type="DESEABLE" requirements={offer.requirements} />
        </div>
      ) : (
        <RetryExtraction offer={offer} />
      )}
    </article>
  );
}

function Detail({ term, value, testId }: { term: string; value: string; testId?: string }) {
  return (
    <div className="flex gap-2" data-testid={testId}>
      <dt className="font-medium text-slate-500">{term}:</dt>
      <dd className="text-slate-800">{value}</dd>
    </div>
  );
}

interface RequirementGroupProps {
  title: string;
  type: RequirementType;
  requirements: Requirement[];
}

function RequirementGroup({ title, type, requirements }: RequirementGroupProps) {
  const headingId = `requirements-${type.toLowerCase()}`;
  const items = requirements.filter((requirement) => requirement.type === type);

  return (
    <section
      aria-labelledby={headingId}
      data-testid={`offer-requirements-${type === 'OBLIGATORIO' ? 'mandatory' : 'optional'}`}
      className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
    >
      <h2 id={headingId} className="text-lg font-semibold text-slate-900">
        {title}
      </h2>
      {items.length === 0 ? (
        <p className="mt-2 text-sm text-slate-500">La oferta no indica ninguno.</p>
      ) : (
        <ul className="mt-3 flex flex-col gap-2">
          {items.map((requirement) => (
            <li key={requirement.id} className="flex items-start justify-between gap-3 text-sm text-slate-800">
              <span>{requirement.description}</span>
              <span className="shrink-0 rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                {CATEGORY_LABELS[requirement.category]}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function RetryExtraction({ offer }: { offer: OfferResponse }) {
  const retry = useRetryExtraction(offer.id);
  const failed = offer.extractionStatus === 'FALLIDA';

  return (
    <section className="flex flex-col items-start gap-3">
      {failed ? (
        <Alert title="No se han podido extraer los datos de la oferta" testId="extraction-failed">
          El texto de la oferta está guardado. Puedes reintentar la extracción.
        </Alert>
      ) : (
        <Alert tone="info" title="La extracción de la oferta no ha terminado" testId="extraction-pending">
          El texto de la oferta está guardado. Puedes reintentar la extracción.
        </Alert>
      )}
      {retry.isError && !(retry.error instanceof ApiError && retry.error.slug === 'offer-already-extracted') && (
        <Alert title={toGeneralError(retry.error).title} testId="retry-extraction-error">
          {toGeneralError(retry.error).detail}
        </Alert>
      )}
      <Button data-testid="retry-extraction-button" onClick={() => retry.mutate()} disabled={retry.isPending}>
        {retry.isPending ? 'Reintentando extracción…' : 'Reintentar extracción'}
      </Button>
    </section>
  );
}

/** El backend ya solo admite http(s); se comprueba otra vez antes de pintarla como enlace (una `javascript:` sería un XSS). */
function isHttpUrl(url: string | null): url is string {
  return url !== null && /^https?:\/\//i.test(url);
}
