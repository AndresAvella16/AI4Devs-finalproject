import type { ApplicationStatus } from '../../api/applications';
import type { RequirementCategory, Salary, WorkMode } from '../../api/offers';

export const WORK_MODE_LABELS: Record<WorkMode, string> = {
  REMOTO: 'Remoto',
  HIBRIDO: 'Híbrido',
  PRESENCIAL: 'Presencial',
  DESCONOCIDO: 'Modalidad no indicada',
};

export const CATEGORY_LABELS: Record<RequirementCategory, string> = {
  TECNICO: 'Técnico',
  IDIOMA: 'Idioma',
  EXPERIENCIA: 'Experiencia',
  FORMACION: 'Formación',
  OTRO: 'Otro',
};

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  GUARDADA: 'Guardada',
  ENVIADA: 'Enviada',
  ENTREVISTA: 'Entrevista',
  OFERTA: 'Oferta',
  DESCARTADA: 'Descartada',
};

/** Título que se muestra cuando la extracción no ha terminado (`title = null`). */
export const UNTITLED_OFFER = 'Oferta sin título';

export function formatDate(isoDateTime: string): string {
  return new Date(isoDateTime).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** "24.000 € – 28.000 €", "Desde 30.000 €" o "Hasta 28.000 €". */
export function formatSalary({ min, max, currency }: Salary): string {
  const format = new Intl.NumberFormat('es-ES', { style: 'currency', currency, maximumFractionDigits: 0 }).format;
  if (min !== null && max !== null) return min === max ? format(min) : `${format(min)} – ${format(max)}`;
  if (min !== null) return `Desde ${format(min)}`;
  if (max !== null) return `Hasta ${format(max)}`;
  return '';
}
