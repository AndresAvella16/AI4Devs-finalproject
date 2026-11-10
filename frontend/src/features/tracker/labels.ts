import type { ApplicationResponse } from '../../api/applications';
import { UNTITLED_OFFER } from '../offers/labels';

/** "20 nov 2026" a partir de una fecha `YYYY-MM-DD`, sin pasar por UTC (no se corre un día según la zona horaria). */
export function formatDay(isoDate: string): string {
  const [year = 1970, month = 1, day = 1] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** Título de la tarjeta: el puesto, o "Oferta sin título" si la extracción no ha terminado. */
export function cardTitle(application: ApplicationResponse): string {
  return application.offerTitle ?? UNTITLED_OFFER;
}
