import type { DocumentType } from '../../api/documents';

interface DocumentLabels {
  /** Nombre de la pestaña. */
  tab: string;
  /** Con artículo, para las frases: "la carta", "el CV adaptado". */
  withArticle: string;
  /** Con "de" y artículo: "de la carta", "del CV adaptado". */
  ofArticle: string;
  generate: string;
  editor: string;
}

export const DOCUMENT_LABELS: Record<DocumentType, DocumentLabels> = {
  CARTA: {
    tab: 'Carta',
    withArticle: 'la carta',
    ofArticle: 'de la carta',
    generate: 'Generar carta',
    editor: 'Texto de la carta (Markdown)',
  },
  CV_ADAPTADO: {
    tab: 'CV adaptado',
    withArticle: 'el CV adaptado',
    ofArticle: 'del CV adaptado',
    generate: 'Generar CV adaptado',
    editor: 'Texto del CV adaptado (Markdown)',
  },
};

export const DOCUMENT_TYPES: readonly DocumentType[] = ['CARTA', 'CV_ADAPTADO'];

/** "Versión 2 · Editada por mí" (HU-04) o "Versión 1 · Generada por la IA". */
export function versionLabel({ version, editedByUser }: { version: number; editedByUser: boolean }): string {
  return `Versión ${version} · ${editedByUser ? 'Editada por mí' : 'Generada por la IA'}`;
}

/** El nombre que usa el backend, por si la respuesta no trae `Content-Disposition`. */
export function fallbackPdfName(type: DocumentType, version: number): string {
  return `${type === 'CARTA' ? 'carta' : 'cv-adaptado'}-v${version}.pdf`;
}
