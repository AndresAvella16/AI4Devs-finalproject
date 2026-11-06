import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/ApiError';

/** No se reintentan los errores 4xx (no van a cambiar); el resto, hasta 2 veces. Las mutaciones nunca. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => {
          const clientError = error instanceof ApiError && error.status >= 400 && error.status < 500;
          return !clientError && failureCount < 2;
        },
        refetchOnWindowFocus: false,
      },
      mutations: { retry: false },
    },
  });
}
