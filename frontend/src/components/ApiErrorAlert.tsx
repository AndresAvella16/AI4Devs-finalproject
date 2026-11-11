import { ApiError } from '../api/ApiError';
import { Alert } from './Alert';
import { formatWait } from './formatWait';
import { toGeneralError } from './serverErrors';

/**
 * Aviso de un error de la API con su `title` y `detail` y, si llega `Retry-After` (429 / 503 de la IA), cuánto hay que
 * esperar para reintentar. `title` sustituye al del error si se indica.
 */
export function ApiErrorAlert({ error, testId, title }: { error: ApiError | Error | null; testId?: string; title?: string }) {
  const general = toGeneralError(error);
  const retryAfter = error instanceof ApiError ? error.retryAfter : null;

  return (
    <Alert title={title ?? general.title} testId={testId}>
      {general.detail && <p>{general.detail}</p>}
      {retryAfter !== null && retryAfter > 0 && <p>Vuelve a intentarlo dentro de {formatWait(retryAfter)}.</p>}
    </Alert>
  );
}
