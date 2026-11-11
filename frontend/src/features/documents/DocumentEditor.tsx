import { zodResolver } from '@hookform/resolvers/zod';
import { useForm, useWatch } from 'react-hook-form';
import { ApiError } from '../../api/ApiError';
import type { DocumentResponse, DocumentType } from '../../api/documents';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { Button } from '../../components/Button';
import { applyServerErrors } from '../../components/serverErrors';
import { TextAreaField } from '../../components/TextAreaField';
import { useToast } from '../../components/useToast';
import { formatDate } from '../offers/labels';
import { DOCUMENT_LABELS, versionLabel } from './labels';
import { CONTENT_MAX, documentVersionSchema, type DocumentVersionForm } from './schemas';
import { useExportDocumentPdf, useSaveDocumentVersion } from './useDocuments';

interface DocumentEditorProps {
  offerId: string;
  type: DocumentType;
  /** La versión que se edita. El padre monta el editor de nuevo (con `key`) cuando cambia. */
  document: DocumentResponse;
  /** Se avisa en cada cambio del texto de si difiere de la versión guardada. */
  onDirtyChange: (dirty: boolean) => void;
  onSaved: (document: DocumentResponse) => void;
  /** Tras un 409 `document-version-conflict`: carga la última versión (se pierden los cambios sin guardar). */
  onLoadLatest: () => void;
  loadingLatest: boolean;
}

const NUMBER = new Intl.NumberFormat('es-ES', { useGrouping: true });

/**
 * Editor de una versión de la carta o del CV adaptado (HU-04). El Markdown se edita y se muestra como texto en un
 * `<textarea>`: nunca se convierte a HTML, así que el contenido de la IA no puede inyectar nada en la página.
 * Guardar crea la versión siguiente ("Editada por mí"); "Exportar PDF" descarga la versión guardada, por eso se
 * desactiva mientras haya cambios sin guardar.
 */
export function DocumentEditor({
  offerId,
  type,
  document,
  onDirtyChange,
  onSaved,
  onLoadLatest,
  loadingLatest,
}: DocumentEditorProps) {
  const labels = DOCUMENT_LABELS[type];
  const { showToast } = useToast();
  const save = useSaveDocumentVersion(offerId, type);
  const exportPdf = useExportDocumentPdf();
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<DocumentVersionForm>({
    resolver: zodResolver(documentVersionSchema),
    defaultValues: { contentMarkdown: document.contentMarkdown },
  });

  const content = useWatch({ control, name: 'contentMarkdown' });
  const dirty = content !== document.contentMarkdown;
  const exportHintId = `document-export-hint-${type}`;
  const conflict = save.error instanceof ApiError && save.error.slug === 'document-version-conflict';
  const fieldErrors = save.error instanceof ApiError && save.error.errors.length > 0;

  const submit = handleSubmit(async ({ contentMarkdown }) => {
    let saved: DocumentResponse;
    try {
      saved = await save.mutateAsync({ baseDocumentId: document.id, contentMarkdown });
    } catch (error) {
      applyServerErrors(error, setError, ['contentMarkdown']);
      return;
    }
    showToast({ tone: 'info', title: `Versión ${saved.version} ${labels.ofArticle} guardada` });
    onSaved(saved);
  });

  return (
    <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-3">
      <p className="text-sm text-slate-600">
        <span data-testid="document-version" className="font-medium text-slate-900">
          {versionLabel(document)}
        </span>
        {' · '}
        <time dateTime={document.createdAt}>{formatDate(document.createdAt)}</time>
      </p>

      <TextAreaField
        id={`document-content-${type}`}
        label={labels.editor}
        testId="document-editor"
        rows={18}
        registration={register('contentMarkdown', {
          onChange: (event: { target: { value: string } }) =>
            onDirtyChange(event.target.value !== document.contentMarkdown),
        })}
        error={errors.contentMarkdown?.message}
        hint={`${NUMBER.format(content.length)} / ${NUMBER.format(CONTENT_MAX)} caracteres`}
        hintTestId="document-char-counter"
      />

      {conflict && (
        <div data-testid="document-conflict" className="flex flex-col items-start gap-2">
          <ApiErrorAlert error={save.error} />
          <p className="text-sm text-slate-600">Si cargas la última versión, perderás los cambios que no has guardado.</p>
          <Button variant="secondary" onClick={onLoadLatest} disabled={loadingLatest}>
            {loadingLatest ? 'Cargando…' : 'Cargar la última versión'}
          </Button>
        </div>
      )}
      {save.isError && !conflict && !fieldErrors && (
        <ApiErrorAlert error={save.error} title="No se han podido guardar los cambios" testId="document-save-error" />
      )}
      {exportPdf.isError && (
        <ApiErrorAlert error={exportPdf.error} title="No se ha podido exportar el PDF" testId="document-export-error" />
      )}

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" data-testid="document-save-button" disabled={!dirty || save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar cambios'}
        </Button>
        <Button
          variant="secondary"
          data-testid="document-export-button"
          onClick={() => exportPdf.mutate(document)}
          disabled={dirty || exportPdf.isPending}
          aria-describedby={dirty ? exportHintId : undefined}
        >
          {exportPdf.isPending ? 'Exportando…' : 'Exportar PDF'}
        </Button>
        {dirty && (
          <p id={exportHintId} className="text-sm text-slate-500">
            Guarda los cambios para exportarlos.
          </p>
        )}
      </div>
    </form>
  );
}
