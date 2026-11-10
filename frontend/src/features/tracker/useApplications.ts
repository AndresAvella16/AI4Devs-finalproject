import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/ApiError';
import {
  changeApplicationStatus,
  createApplication,
  listApplications,
  updateApplication,
  type ApplicationResponse,
  type ApplicationStatus,
  type UpdateApplicationRequest,
} from '../../api/applications';
import { toGeneralError } from '../../components/serverErrors';
import { useToast } from '../../components/useToast';
import { offerKeys } from '../offers/useOffers';
import { placeCard } from './board';

export const applicationKeys = {
  all: ['applications'] as const,
  list: () => [...applicationKeys.all, 'list'] as const,
};

/** `GET /applications`. En la caché se guarda el array tal cual; las columnas se agrupan con `groupByStatus`. */
export function useApplications() {
  return useQuery({ queryKey: applicationKeys.list(), queryFn: ({ signal }) => listApplications(signal) });
}

/** La candidatura de una oferta, si ya está en el tablero (`undefined` mientras se carga el tablero). */
export function useApplicationForOffer(offerId: string) {
  const applications = useApplications();
  return {
    isPending: applications.isPending,
    application: applications.data?.find((card) => card.offerId === offerId) ?? null,
  };
}

/**
 * `POST /applications` desde el detalle de oferta. Un 409 `application-already-exists` (p. ej. guardada desde otra
 * pestaña) se trata como si se acabara de guardar. Tras guardar se avisa con un toast y se recargan el tablero y las
 * ofertas (el dashboard muestra el estado de la candidatura).
 */
export function useCreateApplication(offerId: string) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: applicationKeys.all }),
      queryClient.invalidateQueries({ queryKey: offerKeys.all }),
    ]);

  return useMutation<ApplicationResponse | null, ApiError | Error>({
    mutationFn: async () => {
      try {
        return await createApplication({ offerId });
      } catch (error) {
        if (error instanceof ApiError && error.slug === 'application-already-exists') return null;
        throw error;
      }
    },
    onSuccess: (created) => {
      if (created) {
        // Se añade ya a la caché para que el enlace "Ver en el tablero" aparezca sin esperar a la recarga.
        queryClient.setQueryData<ApplicationResponse[]>(applicationKeys.list(), (cards) => cards && [...cards, created]);
        showToast({ tone: 'info', title: 'Guardada en el tablero', detail: 'La encontrarás en la columna Guardada.' });
      } else {
        showToast({ tone: 'info', title: 'Esta oferta ya estaba en el tablero' });
      }
      return refresh();
    },
    onError: (error) => {
      const { title, detail } = toGeneralError(error);
      showToast({ title, detail });
    },
  });
}

interface OptimisticContext {
  previous: ApplicationResponse[] | undefined;
}

/** Mueve la tarjeta en la caché y devuelve lo que había para poder deshacerlo. */
async function moveInCache(
  queryClient: QueryClient,
  id: string,
  status: ApplicationStatus,
  index: number,
): Promise<OptimisticContext> {
  // Que una carga en curso no pise el movimiento optimista.
  await queryClient.cancelQueries({ queryKey: applicationKeys.list() });
  const previous = queryClient.getQueryData<ApplicationResponse[]>(applicationKeys.list());
  if (previous) queryClient.setQueryData(applicationKeys.list(), placeCard(previous, id, status, index));
  return { previous };
}

function useRollback() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  return (error: ApiError | Error, context: OptimisticContext | undefined) => {
    if (context?.previous) queryClient.setQueryData(applicationKeys.list(), context.previous);
    const { title, detail } = toGeneralError(error);
    showToast({ title, detail });
  };
}

/** Tras escribir se recarga el tablero (el servidor ha renumerado y cambiado versiones) y el dashboard de ofertas. */
function useRefresh() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: applicationKeys.list() }),
      queryClient.invalidateQueries({ queryKey: offerKeys.list() }),
    ]);
}

export interface ChangeStatusVariables {
  application: ApplicationResponse;
  to: ApplicationStatus;
  /** Índice en la columna de destino. */
  index: number;
}

/**
 * `PATCH /applications/{id}/status`, **solo para cambiar de columna**. Optimista: la tarjeta cambia de columna al
 * momento; si el backend lo rechaza (p. ej. 409), vuelve a su sitio y se muestra un toast con el `detail`.
 */
export function useChangeStatus() {
  const queryClient = useQueryClient();
  const rollback = useRollback();
  const refresh = useRefresh();
  return useMutation<ApplicationResponse, ApiError | Error, ChangeStatusVariables, OptimisticContext>({
    mutationFn: ({ application, to, index }) =>
      changeApplicationStatus(application.id, { status: to, boardPosition: index, version: application.version }),
    onMutate: ({ application, to, index }) => moveInCache(queryClient, application.id, to, index),
    onError: (error, _variables, context) => rollback(error, context),
    onSettled: refresh,
  });
}

export interface ReorderVariables {
  application: ApplicationResponse;
  index: number;
}

/**
 * `PATCH /applications/{id}` con `boardPosition` y `version`, para **reordenar dentro de la misma columna**: así el
 * historial de estados no se llena de filas sin cambio de estado. Optimista, como {@link useChangeStatus}.
 */
export function useReorderApplication() {
  const queryClient = useQueryClient();
  const rollback = useRollback();
  const refresh = useRefresh();
  return useMutation<ApplicationResponse, ApiError | Error, ReorderVariables, OptimisticContext>({
    mutationFn: ({ application, index }) =>
      updateApplication(application.id, { boardPosition: index, version: application.version }),
    onMutate: ({ application, index }) => moveInCache(queryClient, application.id, application.status, index),
    onError: (error, _variables, context) => rollback(error, context),
    onSettled: refresh,
  });
}

/** `PATCH /applications/{id}` desde el panel lateral (notas y fechas). No es optimista: el panel espera la respuesta. */
export function useUpdateApplication(applicationId: string) {
  const queryClient = useQueryClient();
  return useMutation<ApplicationResponse, ApiError | Error, UpdateApplicationRequest>({
    mutationFn: (request) => updateApplication(applicationId, request),
    onSuccess: (updated) => {
      queryClient.setQueryData<ApplicationResponse[]>(applicationKeys.list(), (cards) =>
        cards?.map((card) => (card.id === updated.id ? updated : card)),
      );
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: applicationKeys.list() }),
  });
}
