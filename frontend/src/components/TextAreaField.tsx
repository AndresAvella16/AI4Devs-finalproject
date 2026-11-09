import type { ReactNode, TextareaHTMLAttributes } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';

interface TextAreaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id' | 'name'> {
  id: string;
  label: string;
  registration: UseFormRegisterReturn;
  error?: string;
  /** Texto de ayuda que se muestra siempre (p. ej. un contador), también cuando hay error. */
  hint?: ReactNode;
  hintTestId?: string;
  testId?: string;
}

/**
 * Área de texto accesible, hermana de `FormField`: `<label>` asociado, `aria-invalid` y el error y la pista en
 * `aria-describedby` (primero el error).
 */
export function TextAreaField({ id, label, registration, error, hint, hintTestId, testId, ...props }: TextAreaFieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-slate-700">
        {label}
      </label>
      <textarea
        id={id}
        data-testid={testId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`rounded-md border bg-white px-3 py-2 text-slate-900 shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
          error ? 'border-red-500' : 'border-slate-300'
        }`}
        {...props}
        {...registration}
      />
      {error && (
        <p id={errorId} className="text-sm text-red-700">
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} data-testid={hintTestId} className="text-sm text-slate-500">
          {hint}
        </p>
      )}
    </div>
  );
}
