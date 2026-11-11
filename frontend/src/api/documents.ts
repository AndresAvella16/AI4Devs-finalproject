import { apiDownload, apiFetch, type DownloadedFile } from './client';

export type DocumentType = 'CARTA' | 'CV_ADAPTADO';

/** `DocumentResponse` (sección 4): una versión completa de la carta o del CV adaptado, en Markdown. */
export interface DocumentResponse {
  id: string;
  type: DocumentType;
  version: number;
  contentMarkdown: string;
  editedByUser: boolean;
  createdAt: string;
}

/** Elemento de `DocumentHistoryResponse.versions`: una versión sin el contenido. */
export interface DocumentVersionSummary {
  id: string;
  version: number;
  editedByUser: boolean;
  createdAt: string;
}

/** `DocumentHistoryResponse` (sección 4): la última versión completa y todas, de la más reciente a la más antigua. */
export interface DocumentHistoryResponse {
  latest: DocumentResponse;
  versions: DocumentVersionSummary[];
}

/** `GET /offers/{offerId}/documents?type=`. 404 si la oferta aún no tiene ningún documento de ese tipo. */
export function getDocumentHistory(
  offerId: string,
  type: DocumentType,
  signal?: AbortSignal,
): Promise<DocumentHistoryResponse> {
  return apiFetch<DocumentHistoryResponse>(
    `/offers/${encodeURIComponent(offerId)}/documents?type=${encodeURIComponent(type)}`,
    { signal },
  );
}

/** `POST /offers/{offerId}/documents`: la IA genera la versión siguiente. Consume cupo de IA. */
export function generateDocument(offerId: string, type: DocumentType): Promise<DocumentResponse> {
  return apiFetch<DocumentResponse>(`/offers/${encodeURIComponent(offerId)}/documents`, {
    method: 'POST',
    body: { type },
  });
}

/** `POST /documents/{documentId}/versions`: guarda la edición como versión siguiente a la de `documentId`. */
export function createDocumentVersion(documentId: string, contentMarkdown: string): Promise<DocumentResponse> {
  return apiFetch<DocumentResponse>(`/documents/${encodeURIComponent(documentId)}/versions`, {
    method: 'POST',
    body: { contentMarkdown },
  });
}

/** `GET /documents/{documentId}/pdf`: el PDF de esa versión, con el nombre de `Content-Disposition`. */
export function downloadDocumentPdf(documentId: string): Promise<DownloadedFile> {
  return apiDownload(`/documents/${encodeURIComponent(documentId)}/pdf`, 'application/pdf');
}
