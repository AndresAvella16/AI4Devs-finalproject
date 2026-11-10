import { Link } from 'react-router';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { toGeneralError } from '../../components/serverErrors';
import { useAuth } from '../auth/useAuth';
import { useProfile } from '../profile/useProfile';
import { OfferListItem } from './OfferListItem';
import { useOffers } from './useOffers';

const linkButton =
  'inline-block rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600';

/** Dashboard (`/`): ofertas importadas, de la más reciente a la más antigua, con su puntuación de encaje. */
export function OffersDashboardPage() {
  const { session } = useAuth();
  const offers = useOffers();
  // Sin perfil no se puede calcular el encaje (409 profile-required): se invita a crearlo.
  const profile = useProfile();

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm text-slate-600" data-testid="home-greeting">
            Hola, {session?.user.fullName}
          </p>
          <h1 className="text-2xl font-bold text-slate-900">Tus ofertas</h1>
        </div>
        <Link to="/ofertas/nueva" className={linkButton} data-testid="new-offer-button">
          Nueva oferta
        </Link>
      </div>

      {profile.data === null && (
        <div
          data-testid="profile-missing-cta"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-indigo-200 bg-indigo-50 p-4"
        >
          <div>
            <p className="font-semibold text-indigo-900">Crea tu perfil</p>
            <p className="text-sm text-indigo-800">
              Sube tu CV para que podamos calcular tu encaje con cada oferta.
            </p>
          </div>
          <Link to="/perfil" className={linkButton}>
            Crear mi perfil
          </Link>
        </div>
      )}

      {offers.isPending ? (
        <div data-testid="offers-loading" aria-busy="true" className="flex flex-col gap-3">
          <span className="sr-only" role="status">
            Cargando tus ofertas…
          </span>
          {[0, 1, 2].map((key) => (
            <div key={key} className="h-24 animate-pulse rounded-lg bg-slate-200" />
          ))}
        </div>
      ) : offers.isError ? (
        <div data-testid="offers-error" className="flex flex-col items-start gap-3">
          <Alert title="No se han podido cargar tus ofertas">{toGeneralError(offers.error).detail}</Alert>
          <Button variant="secondary" onClick={() => void offers.refetch()} disabled={offers.isFetching}>
            Reintentar
          </Button>
        </div>
      ) : offers.data.length === 0 ? (
        <div
          data-testid="offers-empty"
          className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-slate-300 bg-white p-10 text-center"
        >
          <p className="font-medium text-slate-800">Aún no has importado ninguna oferta</p>
          <p className="text-sm text-slate-600">Pega una oferta y verás cuánto encaja con tu perfil.</p>
          <Link to="/ofertas/nueva" className={linkButton}>
            Importar tu primera oferta
          </Link>
        </div>
      ) : (
        <ul data-testid="offer-list" className="flex flex-col gap-3" aria-label="Ofertas importadas">
          {offers.data.map((offer) => (
            <OfferListItem key={offer.id} offer={offer} />
          ))}
        </ul>
      )}
    </section>
  );
}
