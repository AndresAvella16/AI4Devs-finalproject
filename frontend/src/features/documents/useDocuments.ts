import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/ApiError';
import {
  createDocumentVersion,
  downloadDocumentPdf,
  generateDocument,
  getDocumentHistory,
  type DocumentHistoryResponse,
  type DocumentResponse,
  type DocumentType,
} from '../../api/documents';
import { analysisKeys } from '../analysis/useAnalysis';
import { offerKeys } from '../offers/useOffers';
import { downloadFile } from './downloadFile';
import { fallbackPdfName } from './labels';

export const documentKeys = {
  /** Cuelga del detalle de la oferta, como su análisis. */
  history: (offerId: string, type: DocumentType) => [...offerKeys.detail(offerId), 'documents', type] as const,
};

/** `GET /offers/{offerId}/documents?type=`. `null` si la oferta aún no tiene ningún documento de ese tipo (404). */
export function useDocumentHistory(offerId: string, type: DocumentType) {
  return useQuery<DocumentHistoryResponse | null, ApiError | Error>({
    queryKey: documentKeys.history(offerId, type),
    queryFn: async ({ signal }) => {
      try {
        return await getDocumentHistory(offerId, type, signal);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}

/** Pone la versión nueva como `latest` y al principio del historial, sin esperar a otra petición. */
function addToHistory(history: DocumentHistoryResponse | null | undefined, document: DocumentResponse) {
  const { id, version, editedByUser, createdAt } = document;
  const others = (history?.versions ?? []).filter((item) => item.id !== id);
  return { latest: document, versions: [{ id, version, editedByUser, createdAt }, ...others] };
}

function useAddVersion(offerId: string, type: DocumentType) {
  const queryClient = useQueryClient();
  return (document: DocumentResponse) => {
    queryClient.setQueryData<DocumentHistoryResponse | null>(documentKeys.history(offerId, type), (history) =>
      addToHistory(history, document),
    );
  };
}

/**
 * `POST /offers/{offerId}/documents`. Un 409 `analysis-required` significa que la oferta ya no tiene análisis: se
 * recarga el análisis para que el botón se desactive.
 */
export function useGenerateDocument(offerId: string, type: DocumentType) {
  const queryClient = useQueryClient();
  const addVersion = useAddVersion(offerId, type);
  return useMutation<DocumentResponse, ApiError | Error>({
    mutationFn: () => generateDocument(offerId, type),
    onSuccess: addVersion,
    onError: (error) => {
      if (error instanceof ApiError && error.slug === 'analysis-required') {
        return queryClient.invalidateQueries({ queryKey: analysisKeys.latest(offerId) });
      }
    },
  });
}

interface SaveVersionVariables {
  /** La versión que se estaba editando: el backend crea la siguiente o responde 409 si ya no es la última. */
  baseDocumentId: string;
  contentMarkdown: string;
}

/** `POST /documents/{documentId}/versions`. */
export function useSaveDocumentVersion(offerId: string, type: DocumentType) {
  const addVersion = useAddVersion(offerId, type);
  return useMutation<DocumentResponse, ApiError | Error, SaveVersionVariables>({
    mutationFn: ({ baseDocumentId, contentMarkdown }) => createDocumentVersion(baseDocumentId, contentMarkdown),
    onSuccess: addVersion,
  });
}

/** `GET /documents/{documentId}/pdf` y descarga del fichero con el nombre que da el servidor. */
export function useExportDocumentPdf() {
  return useMutation<undefined, ApiError | Error, DocumentResponse>({
    mutationFn: async (document) => {
      const { blob, fileName } = await downloadDocumentPdf(document.id);
      downloadFile(blob, fileName ?? fallbackPdfName(document.type, document.version));
      return undefined;
    },
  });
}
