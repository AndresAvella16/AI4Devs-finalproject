import type { InputHTMLAttributes } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';

interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'name'> {
  id: string;
  label: string;
  registration: UseFormRegisterReturn;
  error?: string;
  hint?: string;
  testId?: string;
}

/** Campo de formulario accesible: `<label>` asociado, `aria-invalid` y el error (o la pista) en `aria-describedby`. */
export function FormField({ id, label, registration, error, hint, testId, ...inputProps }: FormFieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = error ? errorId : hint ? hintId : undefined;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        data-testid={testId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`rounded-md border bg-white px-3 py-2 text-slate-900 shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
          error ? 'border-red-500' : 'border-slate-300'
        }`}
        {...inputProps}
        {...registration}
      />
      {error ? (
        <p id={errorId} className="text-sm text-red-700">
          {error}
        </p>
      ) : (
        hint && (
          <p id={hintId} className="text-sm text-slate-500">
            {hint}
          </p>
        )
      )}
    </div>
  );
}
