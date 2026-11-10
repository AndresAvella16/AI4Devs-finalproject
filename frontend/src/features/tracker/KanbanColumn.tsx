import { useDroppable } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import type { ApplicationResponse, ApplicationStatus } from '../../api/applications';
import { APPLICATION_STATUS_LABELS } from '../offers/labels';
import { SortableApplicationCard, type OpenDrawer } from './ApplicationCard';

interface KanbanColumnProps {
  status: ApplicationStatus;
  cards: ApplicationResponse[];
  /** Mientras se arrastra: si la tarjeta puede ir a esta columna. `undefined` si no se arrastra nada. */
  dropAllowed: boolean | undefined;
  onOpen: OpenDrawer;
}

/** Columna del kanban: título, contador y lista ordenable. El id de la zona donde soltar es el estado. */
export function KanbanColumn({ status, cards, dropAllowed, onOpen }: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({ id: status });
  const headingId = `kanban-column-${status}-title`;
  const label = APPLICATION_STATUS_LABELS[status];

  const tone =
    dropAllowed === false
      ? 'border-dashed border-slate-300 bg-slate-100 opacity-60'
      : isOver
        ? 'border-indigo-400 bg-indigo-50'
        : dropAllowed
          ? 'border-indigo-200 bg-slate-50'
          : 'border-slate-200 bg-slate-50';

  return (
    <section
      aria-labelledby={headingId}
      data-testid={`kanban-column-${status}`}
      data-drop-allowed={dropAllowed === undefined ? undefined : String(dropAllowed)}
      className={`flex min-w-0 flex-col rounded-lg border p-2 transition-colors ${tone}`}
    >
      <header className="mb-2 flex items-center justify-between gap-2 px-1">
        <h2 id={headingId} className="text-sm font-semibold text-slate-800">
          {label}
        </h2>
        <span className="flex items-center gap-2">
          {dropAllowed === false && <span className="text-xs text-slate-600">No disponible</span>}
          <span
            data-testid="kanban-column-count"
            className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-slate-700"
          >
            {cards.length}
            <span className="sr-only"> {cards.length === 1 ? 'candidatura' : 'candidaturas'}</span>
          </span>
        </span>
      </header>
      <SortableContext id={status} items={cards.map((card) => card.id)} strategy={verticalListSortingStrategy}>
        <ul
          ref={setNodeRef}
          data-status={status}
          aria-labelledby={headingId}
          className="flex min-h-24 flex-1 flex-col gap-2"
        >
          {cards.map((card, index) => (
            <SortableApplicationCard key={card.id} application={card} index={index} onOpen={onOpen} />
          ))}
        </ul>
      </SortableContext>
    </section>
  );
}
