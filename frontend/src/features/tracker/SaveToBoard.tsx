import { Link } from 'react-router';
import { Button } from '../../components/Button';
import { useApplicationForOffer, useCreateApplication } from './useApplications';

const boardLink =
  'inline-block rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600';

/**
 * "Guardar en el tablero" (`POST /applications`) en el detalle de la oferta, o "Ver en el tablero" si ya tiene
 * candidatura (lo sabe por `GET /applications`, la misma consulta del tablero). Vale para cualquier oferta, también
 * PENDIENTE o FALLIDA (sección 4).
 */
export function SaveToBoard({ offerId }: { offerId: string }) {
  const { isPending, application } = useApplicationForOffer(offerId);
  const create = useCreateApplication(offerId);

  if (isPending) return null;
  if (application) {
    return (
      <Link to="/tablero" className={boardLink} data-testid="view-on-board-link">
        Ver en el tablero
      </Link>
    );
  }
  return (
    <Button data-testid="save-to-board-button" onClick={() => create.mutate()} disabled={create.isPending}>
      {create.isPending ? 'Guardando…' : 'Guardar en el tablero'}
    </Button>
  );
}
