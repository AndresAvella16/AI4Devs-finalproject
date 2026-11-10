import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { ReactNode } from 'react';
import type { ApplicationResponse } from '../../api/applications';
import { MatchScoreBadge } from '../../components/MatchScoreBadge';
import { cardTitle, formatDay } from './labels';

export type OpenDrawer = (application: ApplicationResponse, trigger: HTMLElement) => void;

interface ApplicationCardBodyProps {
  application: ApplicationResponse;
  /** Sin él (p. ej. en la copia que se arrastra) el título no es un botón. */
  onOpen?: OpenDrawer;
  children?: ReactNode;
}

/** Contenido de una tarjeta: puesto (abre el panel lateral), empresa, puntuación y fecha de seguimiento. */
export function ApplicationCardBody({ application, onOpen, children }: ApplicationCardBodyProps) {
  const title = <span data-testid="application-card-title">{cardTitle(application)}</span>;

  return (
    <div className="flex min-w-0 flex-1 flex-col gap-2">
      {onOpen ? (
        <button
          type="button"
          onClick={(event) => onOpen(application, event.currentTarget)}
          className="rounded text-left font-semibold text-slate-900 hover:text-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >
          <span className="sr-only">Ver detalles de</span> {title}
        </button>
      ) : (
        <p className="font-semibold text-slate-900">{title}</p>
      )}
      <p className="truncate text-sm text-slate-600">{application.company ?? 'Empresa no indicada'}</p>
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <MatchScoreBadge score={application.score} />
        {application.followUpDate && (
          <span data-testid="application-follow-up" className="text-slate-600">
            Seguimiento: {formatDay(application.followUpDate)}
          </span>
        )}
      </div>
      {application.followUpOverdue && (
        <span
          data-testid="follow-up-overdue"
          className="inline-flex w-fit items-center gap-1 rounded-full border border-amber-400 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-900"
        >
          <span aria-hidden="true">⏰</span> Seguimiento pendiente
        </span>
      )}
      {children}
    </div>
  );
}

const cardClasses = 'flex gap-2 rounded-lg border border-slate-200 bg-white p-3 shadow-sm';

interface SortableApplicationCardProps {
  application: ApplicationResponse;
  index: number;
  onOpen: OpenDrawer;
}

/**
 * Tarjeta arrastrable del kanban. Con el ratón se arrastra desde cualquier punto; con el teclado, desde el asa
 * (Espacio para cogerla, flechas para moverla, Espacio para soltarla y Escape para cancelar). El título abre el panel.
 */
export function SortableApplicationCard({ application, index, onOpen }: SortableApplicationCardProps) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: application.id,
    attributes: { roleDescription: 'tarjeta arrastrable' },
  });

  return (
    <li
      ref={setNodeRef}
      data-testid="application-card"
      data-offer-id={application.offerId}
      data-status={application.status}
      data-index={index}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={`${cardClasses} ${isDragging ? 'opacity-40' : ''}`}
      {...listeners}
    >
      <button
        type="button"
        ref={setActivatorNodeRef}
        {...attributes}
        aria-label={`Mover la candidatura ${cardTitle(application)}`}
        className="h-fit cursor-grab touch-none rounded px-1 text-slate-500 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
      >
        <span aria-hidden="true">⠿</span>
      </button>
      <ApplicationCardBody application={application} onOpen={onOpen} />
    </li>
  );
}

/** Copia de la tarjeta que sigue al puntero mientras se arrastra (`DragOverlay`). */
export function ApplicationCardPreview({ application }: { application: ApplicationResponse }) {
  return (
    <div className={`${cardClasses} cursor-grabbing shadow-lg ring-2 ring-indigo-500`}>
      <ApplicationCardBody application={application} />
    </div>
  );
}
