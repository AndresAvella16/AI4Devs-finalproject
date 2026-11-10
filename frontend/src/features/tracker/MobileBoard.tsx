import { useId, useRef, useState, type KeyboardEvent } from 'react';
import { APPLICATION_STATUSES, type ApplicationResponse, type ApplicationStatus } from '../../api/applications';
import { Button } from '../../components/Button';
import { APPLICATION_STATUS_LABELS } from '../offers/labels';
import { ApplicationCardBody, type OpenDrawer } from './ApplicationCard';
import { groupByStatus, isApplicationStatus } from './board';
import { allowedTargets } from './canTransition';
import { useChangeStatus } from './useApplications';

interface MobileBoardProps {
  cards: ApplicationResponse[];
  onOpen: OpenDrawer;
}

const TAB_KEYS: Record<string, (index: number, last: number) => number> = {
  ArrowRight: (index, last) => (index === last ? 0 : index + 1),
  ArrowLeft: (index, last) => (index === 0 ? last : index - 1),
  Home: () => 0,
  End: (_index, last) => last,
};

/**
 * Tablero en pantallas de menos de 768 px: una pestaña por columna y, en cada tarjeta, el menú "Mover a…" con las
 * columnas a las que puede pasar. La tarjeta queda al final de la columna de destino.
 */
export function MobileBoard({ cards, onOpen }: MobileBoardProps) {
  const [selected, setSelected] = useState<ApplicationStatus>('GUARDADA');
  const tabRefs = useRef(new Map<ApplicationStatus, HTMLButtonElement>());
  const baseId = useId();
  const board = groupByStatus(cards);
  const changeStatus = useChangeStatus();

  // Navegación con flechas, Inicio y Fin del patrón de pestañas de WAI-ARIA.
  const onTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const next = TAB_KEYS[event.key];
    if (!next) return;
    event.preventDefault();
    const status =
      APPLICATION_STATUSES[next(APPLICATION_STATUSES.indexOf(selected), APPLICATION_STATUSES.length - 1)] ?? selected;
    setSelected(status);
    tabRefs.current.get(status)?.focus();
  };

  return (
    <div data-testid="kanban-board-mobile" className="flex flex-col gap-3">
      <div role="tablist" aria-label="Columnas del tablero" className="flex gap-1 overflow-x-auto pb-1">
        {APPLICATION_STATUSES.map((status) => (
          <button
            key={status}
            ref={(element) => {
              if (element) tabRefs.current.set(status, element);
              else tabRefs.current.delete(status);
            }}
            type="button"
            role="tab"
            id={`${baseId}-tab-${status}`}
            aria-selected={status === selected}
            aria-controls={`${baseId}-panel`}
            tabIndex={status === selected ? 0 : -1}
            onClick={() => setSelected(status)}
            onKeyDown={onTabKeyDown}
            className={`flex shrink-0 items-center gap-1 rounded-md px-3 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 ${
              status === selected ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-700'
            }`}
          >
            {APPLICATION_STATUS_LABELS[status]}
            <span className="rounded-full bg-white px-1.5 text-xs text-slate-800">{board[status].length}</span>
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${baseId}-panel`} aria-labelledby={`${baseId}-tab-${selected}`}>
        {board[selected].length === 0 ? (
          <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-600">
            No hay candidaturas en esta columna.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {board[selected].map((card) => (
              <li
                key={card.id}
                data-testid="application-card"
                data-offer-id={card.offerId}
                className="flex rounded-lg border border-slate-200 bg-white p-3 shadow-sm"
              >
                <ApplicationCardBody application={card} onOpen={onOpen}>
                  <MoveTo
                    application={card}
                    onMove={(to) => changeStatus.mutate({ application: card, to, index: board[to].length })}
                  />
                </ApplicationCardBody>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

interface MoveToProps {
  application: ApplicationResponse;
  onMove: (to: ApplicationStatus) => void;
}

/** "Mover a…": solo ofrece las columnas permitidas por `canTransition` (el backend sigue decidiendo). */
function MoveTo({ application, onMove }: MoveToProps) {
  const [target, setTarget] = useState('');
  const id = useId();

  return (
    <div className="flex items-end gap-2">
      <div className="flex flex-1 flex-col gap-1">
        <label htmlFor={id} className="text-xs font-medium text-slate-700">
          Mover a…
        </label>
        <select
          id={id}
          value={target}
          onChange={(event) => setTarget(event.target.value)}
          className="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
        >
          <option value="">Elige columna</option>
          {allowedTargets(application.status).map((status) => (
            <option key={status} value={status}>
              {APPLICATION_STATUS_LABELS[status]}
            </option>
          ))}
        </select>
      </div>
      <Button
        variant="secondary"
        disabled={!isApplicationStatus(target)}
        onClick={() => {
          if (isApplicationStatus(target)) onMove(target);
          setTarget('');
        }}
      >
        Mover
      </Button>
    </div>
  );
}
