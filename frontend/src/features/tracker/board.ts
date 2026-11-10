import { APPLICATION_STATUSES, type ApplicationResponse, type ApplicationStatus } from '../../api/applications';

export type Board = Record<ApplicationStatus, ApplicationResponse[]>;

export function isApplicationStatus(value: unknown): value is ApplicationStatus {
  return typeof value === 'string' && (APPLICATION_STATUSES as readonly string[]).includes(value);
}

/** Las 5 columnas, cada una ordenada por `boardPosition` (a igualdad, en el orden en que llegan de la API). */
export function groupByStatus(cards: readonly ApplicationResponse[]): Board {
  const board = Object.fromEntries(APPLICATION_STATUSES.map((status) => [status, [] as ApplicationResponse[]])) as Board;
  for (const card of cards) board[card.status].push(card);
  for (const status of APPLICATION_STATUSES) board[status].sort((a, b) => a.boardPosition - b.boardPosition);
  return board;
}

/**
 * Lo mismo que `BoardColumn.place` en el servidor, para la actualización optimista: coloca la tarjeta en `index`
 * dentro de la columna `status` (al final si el índice es mayor que la columna) y renumera de 0 a n−1 la columna de
 * destino y, si cambia de columna, la de origen. Devuelve un array nuevo.
 */
export function placeCard(
  cards: readonly ApplicationResponse[],
  id: string,
  status: ApplicationStatus,
  index: number,
): ApplicationResponse[] {
  const moved = cards.find((card) => card.id === id);
  if (!moved) return [...cards];
  const board = groupByStatus(cards.filter((card) => card.id !== id));
  const destination = board[status];
  destination.splice(Math.min(index, destination.length), 0, { ...moved, status });

  const positions = new Map<string, ApplicationResponse>();
  for (const column of APPLICATION_STATUSES) {
    board[column].forEach((card, position) => positions.set(card.id, { ...card, boardPosition: position }));
  }
  return cards.map((card) => positions.get(card.id) ?? card);
}

export type DropAction =
  | { kind: 'status'; to: ApplicationStatus; index: number }
  | { kind: 'reorder'; index: number };

/**
 * Qué hacer al soltar la tarjeta `activeId` sobre `overId` (otra tarjeta o una columna, cuyo id es el estado).
 * Sobre una tarjeta, ocupa su índice; sobre una columna, va al final. No filtra las transiciones prohibidas: las
 * propone igualmente y decide el backend.
 */
export function resolveDrop(
  cards: readonly ApplicationResponse[],
  activeId: string,
  overId: string | null,
): DropAction | null {
  const active = cards.find((card) => card.id === activeId);
  if (!active || overId === null || overId === activeId) return null;
  const board = groupByStatus(cards);

  let to: ApplicationStatus;
  let index: number;
  if (isApplicationStatus(overId)) {
    to = overId;
    const column = board[to].filter((card) => card.id !== activeId);
    index = column.length;
  } else {
    const over = cards.find((card) => card.id === overId);
    if (!over) return null;
    to = over.status;
    index = board[to].findIndex((card) => card.id === overId);
  }

  if (to !== active.status) return { kind: 'status', to, index };
  const currentIndex = board[to].findIndex((card) => card.id === activeId);
  return index === currentIndex ? null : { kind: 'reorder', index };
}
