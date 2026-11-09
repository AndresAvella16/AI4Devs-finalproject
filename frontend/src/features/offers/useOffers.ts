import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/ApiError';
import {
  createOffer,
  getOffer,
  listOffers,
  retryExtraction,
  type CreateOfferRequest,
  type OfferResponse,
} from '../../api/offers';

export const offerKeys = {
  all: ['offers'] as const,
  list: () => [...offerKeys.all, 'list'] as const,
  detail: (offerId: string) => [...offerKeys.all, 'detail', offerId] as const,
};

/** `GET /offers` (dashboard). */
export function useOffers() {
  return useQuery({ queryKey: offerKeys.list(), queryFn: ({ signal }) => listOffers(signal) });
}

/** `GET /offers/{offerId}`. */
export function useOffer(offerId: string) {
  return useQuery({ queryKey: offerKeys.detail(offerId), queryFn: ({ signal }) => getOffer(offerId, signal) });
}

/** `POST /offers`. Deja la oferta creada en la caché (el detalle se abre sin otra petición) y refresca el dashboard. */
export function useCreateOffer() {
  const queryClient = useQueryClient();
  return useMutation<OfferResponse, ApiError | Error, CreateOfferRequest>({
    mutationFn: createOffer,
    onSuccess: (offer) => {
      queryClient.setQueryData(offerKeys.detail(offer.id), offer);
      return queryClient.invalidateQueries({ queryKey: offerKeys.list() });
    },
  });
}

/**
 * `POST /offers/{offerId}/extraction`. Un 409 `offer-already-extracted` significa que la oferta ya está COMPLETADA
 * (p. ej. desde otra pestaña): se recarga y desaparece el botón.
 */
export function useRetryExtraction(offerId: string) {
  const queryClient = useQueryClient();
  return useMutation<OfferResponse, ApiError | Error>({
    mutationFn: () => retryExtraction(offerId),
    onSuccess: (offer) => {
      queryClient.setQueryData(offerKeys.detail(offerId), offer);
      return queryClient.invalidateQueries({ queryKey: offerKeys.list() });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.slug === 'offer-already-extracted') {
        return queryClient.invalidateQueries({ queryKey: offerKeys.all });
      }
    },
  });
}
