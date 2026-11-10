import type { ReactNode } from 'react';
import { Button } from '../../components/Button';

interface ListSectionProps {
  title: string;
  emptyText: string;
  isEmpty: boolean;
  addLabel: string;
  addTestId: string;
  onAdd: () => void;
  canAdd: boolean;
  error?: string;
  children: ReactNode;
}

/** Sección de una lista editable del perfil (experiencias, formación, idiomas): título, elementos y "Añadir…". */
export function ListSection({ title, emptyText, isEmpty, addLabel, addTestId, onAdd, canAdd, error, children }: ListSectionProps) {
  return (
    <section className="flex flex-col gap-3" aria-label={title}>
      <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
      {isEmpty && <p className="text-sm text-slate-500">{emptyText}</p>}
      {children}
      {error && <p className="text-sm text-red-700">{error}</p>}
      <div>
        <Button variant="secondary" data-testid={addTestId} onClick={onAdd} disabled={!canAdd}>
          {addLabel}
        </Button>
      </div>
    </section>
  );
}

interface ListItemProps {
  legend: string;
  removeLabel: string;
  onRemove: () => void;
  testId: string;
  children: ReactNode;
}

/** Un elemento de la lista: `<fieldset>` con su `<legend>` ("Experiencia 2") y el botón para quitarlo. */
export function ListItem({ legend, removeLabel, onRemove, testId, children }: ListItemProps) {
  return (
    <fieldset data-testid={testId} className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-4">
      <legend className="sr-only">{legend}</legend>
      <div className="flex items-center justify-between gap-2">
        <p aria-hidden="true" className="text-sm font-semibold text-slate-700">
          {legend}
        </p>
        <Button variant="secondary" onClick={onRemove} className="px-3 py-1">
          {removeLabel}
        </Button>
      </div>
      {children}
    </fieldset>
  );
}
