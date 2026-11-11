import type { DocumentVersionSummary } from '../../api/documents';
import { formatDate } from '../offers/labels';
import { versionLabel } from './labels';

interface DocumentVersionListProps {
  versions: DocumentVersionSummary[];
  currentId: string;
  headingId: string;
}

/**
 * Historial de versiones, de la más reciente a la más antigua. Solo de lectura: la API no devuelve el contenido de
 * las versiones antiguas (sección 4, `DocumentHistoryResponse`).
 */
export function DocumentVersionList({ versions, currentId, headingId }: DocumentVersionListProps) {
  return (
    <section aria-labelledby={headingId} data-testid="document-versions">
      <h3 id={headingId} className="text-sm font-semibold text-slate-900">
        Historial de versiones
      </h3>
      <ol className="mt-2 flex flex-col gap-1 text-sm text-slate-700">
        {versions.map((item) => {
          const current = item.id === currentId;
          return (
            <li
              key={item.id}
              data-testid="document-version-item"
              aria-current={current ? 'true' : undefined}
              className={current ? 'font-medium text-slate-900' : undefined}
            >
              {versionLabel(item)} · <time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time>
              {current && <span className="text-slate-500"> (la que ves)</span>}
            </li>
          );
        })}
      </ol>
    </section>
  );
}
