import { useRef, useState, type KeyboardEvent } from 'react';
import type { DocumentResponse, DocumentType } from '../../api/documents';
import { Alert } from '../../components/Alert';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { Button } from '../../components/Button';
import { toGeneralError } from '../../components/serverErrors';
import { useLatestAnalysis } from '../analysis/useAnalysis';
import { DocumentEditor } from './DocumentEditor';
import { DocumentVersionList } from './DocumentVersionList';
import { DOCUMENT_LABELS, DOCUMENT_TYPES } from './labels';
import { useDocumentHistory, useGenerateDocument } from './useDocuments';

/**
 * Carta y CV adaptado de una oferta (HU-04, JP-FE-05): dos pestañas (patrón ARIA *tabs*: flechas, Inicio y Fin) con
 * un editor cada una. Las dos pestañas se quedan montadas (la oculta, con `hidden`), así que los cambios sin guardar
 * se conservan al cambiar de una a otra.
 */
export function DocumentsPanel({ offerId }: { offerId: string }) {
  const [selected, setSelected] = useState<DocumentType>('CARTA');
  const tabs = useRef(new Map<DocumentType, HTMLButtonElement>());

  const select = (type: DocumentType) => {
    setSelected(type);
    tabs.current.get(type)?.focus();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const index = DOCUMENT_TYPES.indexOf(selected);
    const last = DOCUMENT_TYPES.length - 1;
    const target = {
      ArrowRight: index === last ? 0 : index + 1,
      ArrowLeft: index === 0 ? last : index - 1,
      Home: 0,
      End: last,
    }[event.key];
    if (target === undefined) return;
    const next = DOCUMENT_TYPES[target];
    if (next === undefined) return;
    event.preventDefault();
    select(next);
  };

  return (
    <section
      aria-labelledby="documents-heading"
      data-testid="documents-panel"
      className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
    >
      <div>
        <h2 id="documents-heading" className="text-lg font-semibold text-slate-900">
          Documentos
        </h2>
        <p className="text-sm text-slate-600">
          Genera una carta y un CV adaptados a esta oferta a partir de tu perfil y del análisis de encaje.
        </p>
      </div>

      <div role="tablist" aria-label="Tipo de documento" className="flex gap-1 border-b border-slate-200">
        {DOCUMENT_TYPES.map((type) => {
          const active = type === selected;
          return (
            <button
              key={type}
              ref={(element) => {
                if (element) tabs.current.set(type, element);
                else tabs.current.delete(type);
              }}
              type="button"
              role="tab"
              id={`document-tab-${type}`}
              data-testid={`document-tab-${type}`}
              aria-selected={active}
              aria-controls={`document-panel-${type}`}
              tabIndex={active ? 0 : -1}
              onClick={() => select(type)}
              onKeyDown={onKeyDown}
              className={`-mb-px rounded-t-md border-b-2 px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 ${
                active ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-600 hover:text-slate-900'
              }`}
            >
              {DOCUMENT_LABELS[type].tab}
            </button>
          );
        })}
      </div>

      {DOCUMENT_TYPES.map((type) => (
        <div
          key={type}
          role="tabpanel"
          id={`document-panel-${type}`}
          data-testid={`document-panel-${type}`}
          aria-labelledby={`document-tab-${type}`}
          hidden={type !== selected}
          tabIndex={0}
          className="focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >
          <DocumentTab offerId={offerId} type={type} />
        </div>
      ))}
    </section>
  );
}

/**
 * Una pestaña: generar (o volver a generar) con la IA, editar la versión mostrada y su historial.
 *
 * <p>La versión que se edita se "fija" en cuanto el usuario cambia el texto, al guardar o al generar: así, si la
 * caché trae otra versión más nueva (p. ej. de otra pestaña), no se pierde lo que está escribiendo y al guardar el
 * backend responde 409 `document-version-conflict`.
 */
function DocumentTab({ offerId, type }: { offerId: string; type: DocumentType }) {
  const labels = DOCUMENT_LABELS[type];
  const analysis = useLatestAnalysis(offerId);
  const history = useDocumentHistory(offerId, type);
  const generate = useGenerateDocument(offerId, type);
  const [pinned, setPinned] = useState<DocumentResponse | null>(null);
  const [dirty, setDirty] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [loadingLatest, setLoadingLatest] = useState(false);

  const shown = pinned ?? history.data?.latest ?? null;
  const noAnalysis = analysis.isSuccess && analysis.data === null;
  const generateHintId = `document-generate-hint-${type}`;

  const show = (document: DocumentResponse) => {
    setPinned(document);
    setDirty(false);
  };

  const runGenerate = () => {
    setConfirming(false);
    generate.mutate(undefined, { onSuccess: show });
  };

  const onDirtyChange = (value: boolean) => {
    setDirty(value);
    if (value && pinned === null && shown !== null) setPinned(shown);
  };

  const loadLatest = async () => {
    setLoadingLatest(true);
    try {
      const result = await history.refetch();
      if (result.data) show(result.data.latest);
    } finally {
      setLoadingLatest(false);
    }
  };

  return (
    <div className="flex flex-col gap-4 pt-2">
      <div className="flex flex-wrap items-center gap-3">
        <Button
          data-testid="document-generate-button"
          variant={shown ? 'secondary' : 'primary'}
          onClick={() => (dirty ? setConfirming(true) : runGenerate())}
          disabled={analysis.isPending || noAnalysis || generate.isPending}
          aria-describedby={generateHintId}
        >
          {generate.isPending ? 'Generando…' : shown ? 'Volver a generar' : labels.generate}
        </Button>
        <p id={generateHintId} className="text-xs text-slate-500">
          {noAnalysis
            ? `Calcula primero tu encaje con la oferta para generar ${labels.withArticle}.`
            : 'Cada generación consume una consulta de IA (30 por hora).'}
        </p>
      </div>

      {confirming && (
        <div
          data-testid="document-regenerate-confirm"
          className="flex flex-col gap-3 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900"
        >
          <p>
            Perderás los cambios que no has guardado. ¿Quieres volver a generar {labels.withArticle}? Se creará una
            versión nueva.
          </p>
          <div className="flex gap-2">
            <Button onClick={runGenerate}>Sí, volver a generar</Button>
            <Button variant="secondary" onClick={() => setConfirming(false)}>
              Cancelar
            </Button>
          </div>
        </div>
      )}

      {generate.isPending && (
        <p role="status" data-testid="document-generating" className="text-sm font-medium text-indigo-700">
          Generando {labels.withArticle}…
        </p>
      )}

      {generate.isError && <ApiErrorAlert error={generate.error} testId="document-generate-error" />}

      {history.isPending && (
        <div data-testid="document-loading" aria-busy="true" className="flex flex-col gap-2">
          <span className="sr-only" role="status">
            Cargando {labels.withArticle}…
          </span>
          <div className="h-64 animate-pulse rounded bg-slate-200" />
        </div>
      )}

      {history.isError && (
        <div className="flex flex-col items-start gap-3">
          <Alert title={`No se ha podido cargar ${labels.withArticle}`} testId="document-load-error">
            {toGeneralError(history.error).detail}
          </Alert>
          <Button variant="secondary" onClick={() => void history.refetch()} disabled={history.isFetching}>
            Reintentar
          </Button>
        </div>
      )}

      {history.isSuccess && shown === null && !generate.isPending && (
        <p data-testid="document-empty" className="text-sm text-slate-600">
          Aún no has generado {labels.withArticle} de esta oferta.
        </p>
      )}

      {shown && (
        <>
          <DocumentEditor
            key={shown.id}
            offerId={offerId}
            type={type}
            document={shown}
            onDirtyChange={onDirtyChange}
            onSaved={show}
            onLoadLatest={() => void loadLatest()}
            loadingLatest={loadingLatest}
          />
          {history.data && (
            <DocumentVersionList
              versions={history.data.versions}
              currentId={shown.id}
              headingId={`document-versions-${type}`}
            />
          )}
        </>
      )}
    </div>
  );
}
