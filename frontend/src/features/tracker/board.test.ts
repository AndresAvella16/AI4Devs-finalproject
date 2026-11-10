import { describe, expect, it } from 'vitest';
import type { ApplicationResponse } from '../../api/applications';
import { seedApplication, seedApplications } from '../../test/handlers';
import { groupByStatus, placeCard, resolveDrop } from './board';

const angular = seedApplication(1);
const kotlin = seedApplication(2);
const fullStack = seedApplication(3);
const java = seedApplication(4);
const oferta = seedApplication(5);

function idsByStatus(cards: ApplicationResponse[]) {
  const board = groupByStatus(cards);
  return Object.fromEntries(Object.entries(board).map(([status, column]) => [status, column.map((card) => card.id)]));
}

describe('groupByStatus', () => {
  it('agrupa en las 5 columnas y ordena por boardPosition aunque la API las mande desordenadas', () => {
    const board = groupByStatus([fullStack, { ...kotlin, boardPosition: 0 }, { ...angular, boardPosition: 1 }, oferta]);
    expect(Object.keys(board)).toEqual(['GUARDADA', 'ENVIADA', 'ENTREVISTA', 'OFERTA', 'DESCARTADA']);
    expect(board.GUARDADA.map((card) => card.id)).toEqual([kotlin.id, angular.id]);
    expect(board.ENVIADA).toHaveLength(1);
    expect(board.DESCARTADA).toEqual([]);
  });
});

describe('placeCard — lo mismo que hace BoardColumn en el servidor', () => {
  const cards = seedApplications();

  it('cambia de columna, inserta en el índice pedido y renumera origen y destino', () => {
    const result = placeCard(cards, angular.id, 'ENVIADA', 0);
    expect(idsByStatus(result)).toMatchObject({ GUARDADA: [kotlin.id], ENVIADA: [angular.id, fullStack.id] });
    expect(result.find((card) => card.id === kotlin.id)?.boardPosition).toBe(0);
    expect(result.find((card) => card.id === fullStack.id)?.boardPosition).toBe(1);
    expect(result.find((card) => card.id === angular.id)).toMatchObject({ status: 'ENVIADA', boardPosition: 0 });
  });

  it('un índice mayor que la columna deja la tarjeta al final', () => {
    const result = placeCard(cards, angular.id, 'ENVIADA', 99);
    expect(idsByStatus(result).ENVIADA).toEqual([fullStack.id, angular.id]);
  });

  it('reordena dentro de la misma columna', () => {
    const result = placeCard(cards, angular.id, 'GUARDADA', 1);
    expect(idsByStatus(result).GUARDADA).toEqual([kotlin.id, angular.id]);
  });

  it('no modifica el array original', () => {
    placeCard(cards, angular.id, 'ENVIADA', 0);
    expect(cards).toEqual(seedApplications());
  });
});

describe('resolveDrop — qué hacer al soltar una tarjeta', () => {
  const cards = seedApplications();

  it('sobre una tarjeta de otra columna: cambio de estado al índice de esa tarjeta', () => {
    expect(resolveDrop(cards, angular.id, fullStack.id)).toEqual({ kind: 'status', to: 'ENVIADA', index: 0 });
  });

  it('sobre una columna (también vacía): cambio de estado al final', () => {
    expect(resolveDrop(cards, angular.id, 'ENVIADA')).toEqual({ kind: 'status', to: 'ENVIADA', index: 1 });
    expect(resolveDrop(cards, oferta.id, 'DESCARTADA')).toEqual({ kind: 'status', to: 'DESCARTADA', index: 0 });
  });

  it('también devuelve las transiciones prohibidas: las filtra quien la llama, con canTransition', () => {
    expect(resolveDrop(cards, oferta.id, 'GUARDADA')).toEqual({ kind: 'status', to: 'GUARDADA', index: 2 });
  });

  it('sobre otra tarjeta de su columna: reordenación', () => {
    expect(resolveDrop(cards, angular.id, kotlin.id)).toEqual({ kind: 'reorder', index: 1 });
    expect(resolveDrop(cards, kotlin.id, angular.id)).toEqual({ kind: 'reorder', index: 0 });
  });

  it('sin cambio (misma posición, fuera del tablero o sobre sí misma) no hace nada', () => {
    expect(resolveDrop(cards, angular.id, null)).toBeNull();
    expect(resolveDrop(cards, angular.id, angular.id)).toBeNull();
    expect(resolveDrop(cards, java.id, 'ENTREVISTA')).toBeNull();
    expect(resolveDrop(cards, 'no-existe', 'ENVIADA')).toBeNull();
  });
});
