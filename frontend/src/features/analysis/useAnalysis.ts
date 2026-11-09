import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createAnalysis, getLatestAnalysis, type AnalysisResponse } from '../../api/analysis';
import { ApiError } from '../../api/ApiError';
import { offerKeys } from '../offers/useOffers';

export const analysisKeys = {
  /** Cuelga del detalle de la oferta: lo que invalida la oferta invalida también su análisis. */
  latest: (offerId: string) => [...offerKeys.detail(offerId), 'analysis'] as const,
};

/** `GET /offers/{offerId}/analysis`. `null` si la oferta aún no tiene análisis (404). */
export function useLatestAnalysis(offerId: string) {
  return useQuery<AnalysisResponse | null, ApiError | Error>({
    queryKey: analysisKeys.latest(offerId),
    queryFn: async ({ signal }) => {
      try {
        return await getLatestAnalysis(offerId, signal);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}

/**
 * `POST /offers/{offerId}/analysis`. El nuevo análisis sustituye al mostrado y se refresca el dashboard
 * (`latestScore`). Un 409 `offer-not-extracted` significa que la oferta ya no tiene requisitos: se recarga.
 * El 409 `profile-required` lo gestiona la vista (redirige a crear el perfil).
 */
export function useCreateAnalysis(offerId: string) {
  const queryClient = useQueryClient();
  return useMutation<AnalysisResponse, ApiError | Error>({
    mutationFn: () => createAnalysis(offerId),
    onSuccess: (analysis) => {
      queryClient.setQueryData(analysisKeys.latest(offerId), analysis);
      return queryClient.invalidateQueries({ queryKey: offerKeys.list() });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.slug === 'offer-not-extracted') {
        return queryClient.invalidateQueries({ queryKey: offerKeys.all });
      }
    },
  });
}
