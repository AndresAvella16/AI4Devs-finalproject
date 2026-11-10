import {
  closestCorners,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
  type DragStartEvent,
  type ScreenReaderInstructions,
  type UniqueIdentifier,
} from '@dnd-kit/core';
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { useState } from 'react';
import { APPLICATION_STATUSES, type ApplicationResponse } from '../../api/applications';
import { useToast } from '../../components/useToast';
import { APPLICATION_STATUS_LABELS } from '../offers/labels';
import { ApplicationCardPreview, type OpenDrawer } from './ApplicationCard';
import { groupByStatus, isApplicationStatus, resolveDrop } from './board';
import { canTransition } from './canTransition';
import { KanbanColumn } from './KanbanColumn';
import { cardTitle } from './labels';
import { useChangeStatus, useReorderApplication } from './useApplications';

const screenReaderInstructions: ScreenReaderInstructions = {
  draggable:
    'Para mover la tarjeta, pulsa Espacio. Muévela con las flechas y pulsa Espacio para soltarla, o Escape para cancelar.',
};

/** Las reglas de la HU-05, para explicar en el toast por qué no se puede soltar ahí. */
const TRANSITION_RULES =
  'Solo se avanza a la columna siguiente; se puede descartar desde cualquier columna y reabrir una descartada.';

interface KanbanBoardProps {
  cards: ApplicationResponse[];
  onOpen: OpenDrawer;
}

/**
 * Tablero de escritorio: 5 columnas con dnd-kit, con ratón (`PointerSensor`) y con teclado (`KeyboardSensor`).
 * Al soltar en otra columna se cambia el estado (`PATCH /status`); dentro de la misma columna, se reordena
 * (`PATCH /applications/{id}`). Mientras se arrastra, las columnas no válidas según `canTransition` se atenúan; si se
 * suelta en una de ellas no se llama al backend: la tarjeta vuelve a su sitio y un toast explica por qué. El 409 del
 * backend se sigue tratando (vuelta atrás y toast con el `detail`) como red de seguridad.
 */
export function KanbanBoard({ cards, onOpen }: KanbanBoardProps) {
  const [activeId, setActiveId] = useState<string | null>(null);
  const { showToast } = useToast();
  const changeStatus = useChangeStatus();
  const reorder = useReorderApplication();
  const sensors = useSensors(
    // Con 5 px de margen, un clic en el título abre el panel en lugar de empezar a arrastrar.
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const board = groupByStatus(cards);
  const active = cards.find((card) => card.id === activeId) ?? null;

  const titleOf = (id: UniqueIdentifier): string => {
    const card = cards.find((candidate) => candidate.id === id);
    return card ? cardTitle(card) : 'la tarjeta';
  };
  const describe = (id: UniqueIdentifier | undefined): string => {
    if (id === undefined) return 'ninguna columna';
    if (isApplicationStatus(id)) return `la columna ${APPLICATION_STATUS_LABELS[id]}`;
    const card = cards.find((candidate) => candidate.id === id);
    if (!card) return 'ninguna columna';
    const position = board[card.status].findIndex((candidate) => candidate.id === id) + 1;
    return `la columna ${APPLICATION_STATUS_LABELS[card.status]}, posición ${position}`;
  };
  const announcements: Announcements = {
    onDragStart: ({ active: dragged }) => `Has cogido ${titleOf(dragged.id)}.`,
    onDragOver: ({ active: dragged, over }) => `${titleOf(dragged.id)} está sobre ${describe(over?.id)}.`,
    onDragEnd: ({ active: dragged, over }) => `Has soltado ${titleOf(dragged.id)} en ${describe(over?.id)}.`,
    onDragCancel: ({ active: dragged }) => `Movimiento cancelado: ${titleOf(dragged.id)} vuelve a su sitio.`,
  };

  const handleDragStart = ({ active: dragged }: DragStartEvent) => setActiveId(String(dragged.id));

  const handleDragEnd = ({ active: dragged, over }: DragEndEvent) => {
    setActiveId(null);
    const application = cards.find((card) => card.id === dragged.id);
    const action = resolveDrop(cards, String(dragged.id), over ? String(over.id) : null);
    if (!application || !action) return;
    if (action.kind === 'status' && !canTransition(application.status, action.to)) {
      showToast({
        title: 'Movimiento no permitido',
        detail: `No se puede pasar de ${APPLICATION_STATUS_LABELS[application.status]} a ${APPLICATION_STATUS_LABELS[action.to]}. ${TRANSITION_RULES}`,
      });
      return;
    }
    if (action.kind === 'status') changeStatus.mutate({ application, to: action.to, index: action.index });
    else reorder.mutate({ application, index: action.index });
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setActiveId(null)}
      accessibility={{ announcements, screenReaderInstructions }}
    >
      <div data-testid="kanban-board" className="grid grid-cols-5 gap-3">
        {APPLICATION_STATUSES.map((status) => (
          <KanbanColumn
            key={status}
            status={status}
            cards={board[status]}
            dropAllowed={active ? status === active.status || canTransition(active.status, status) : undefined}
            onOpen={onOpen}
          />
        ))}
      </div>
      <DragOverlay>{active && <ApplicationCardPreview application={active} />}</DragOverlay>
    </DndContext>
  );
}
