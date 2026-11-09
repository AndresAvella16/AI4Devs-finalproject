import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '../api/ApiError';

/**
 * Lleva a los campos del formulario los errores del backend que se pueden asociar a uno:
 * los `errors[]` de un 400 y, si se indica, un slug concreto (p. ej. 409 `email-already-registered` → email).
 * Devuelve `true` si ha marcado algún campo; si no, el formulario muestra un aviso general.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
  slugToField: Partial<Record<string, Path<T>>> = {},
): boolean {
  if (!(error instanceof ApiError)) return false;

  const slugField = error.slug === null ? undefined : slugToField[error.slug];
  if (slugField !== undefined) {
    setError(slugField, { type: 'server', message: error.detail ?? error.title }, { shouldFocus: true });
    return true;
  }

  let applied = false;
  for (const { field, message } of error.errors) {
    const target = fields.find((name) => name === field);
    if (target !== undefined) {
      setError(target, { type: 'server', message }, { shouldFocus: !applied });
      applied = true;
    }
  }
  return applied;
}

/** Aviso general de un formulario cuando el error no se puede asociar a ningún campo. */
export interface GeneralError {
  title: string;
  detail: string | null;
}

export function toGeneralError(error: unknown): GeneralError {
  if (error instanceof ApiError) return { title: error.title, detail: error.detail };
  return { title: 'Ha ocurrido un error inesperado', detail: 'Inténtalo de nuevo en unos segundos.' };
}
