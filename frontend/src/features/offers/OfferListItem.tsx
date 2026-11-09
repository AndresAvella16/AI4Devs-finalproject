import { Link } from 'react-router';
import type { OfferSummary } from '../../api/offers';
import { MatchScoreBadge } from '../../components/MatchScoreBadge';
import { APPLICATION_STATUS_LABELS, formatDate, UNTITLED_OFFER } from './labels';

const EXTRACTION_LABELS = { PENDIENTE: 'Extracción pendiente', FALLIDA: 'Extracción fallida' } as const;

interface OfferListItemProps {
  offer: OfferSummary;
}

/** Fila del dashboard. Toda la tarjeta es el enlace al detalle. */
export function OfferListItem({ offer }: OfferListItemProps) {
  const extractionLabel = offer.extractionStatus === 'COMPLETADA' ? null : EXTRACTION_LABELS[offer.extractionStatus];

  return (
    <li data-testid="offer-item" data-offer-id={offer.id}>
      <Link
        to={`/ofertas/${offer.id}`}
        className="flex items-start justify-between gap-4 rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition-colors hover:border-indigo-300 hover:bg-indigo-50/40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
      >
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-slate-900">{offer.title ?? UNTITLED_OFFER}</h2>
          <p className="text-sm text-slate-600">{offer.company ?? 'Empresa no indicada'}</p>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
            <time dateTime={offer.createdAt} className="text-slate-500">
              Importada el {formatDate(offer.createdAt)}
            </time>
            {extractionLabel && (
              <span
                data-testid="extraction-status"
                className={`rounded px-2 py-0.5 font-medium ${
                  offer.extractionStatus === 'FALLIDA' ? 'bg-red-100 text-red-800' : 'bg-sky-100 text-sky-800'
                }`}
              >
                {extractionLabel}
              </span>
            )}
            {offer.applicationStatus && (
              <span data-testid="application-status" className="rounded bg-indigo-100 px-2 py-0.5 font-medium text-indigo-800">
                <span className="sr-only">Candidatura: </span>
                {APPLICATION_STATUS_LABELS[offer.applicationStatus]}
              </span>
            )}
            {offer.hasBlockingGap && (
              <span data-testid="blocking-gap" className="rounded bg-red-100 px-2 py-0.5 font-medium text-red-800">
                ⚠ Requisito bloqueante
              </span>
            )}
          </div>
        </div>
        <MatchScoreBadge score={offer.latestScore} />
      </Link>
    </li>
  );
}
