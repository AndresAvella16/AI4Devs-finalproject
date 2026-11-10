import { describe, expect, it } from 'vitest';
import { APPLICATION_STATUSES, type ApplicationStatus } from '../../api/applications';
import { allowedTargets, canTransition } from './canTransition';

/** Las transiciones permitidas por `ApplicationStatusPolicy` (HU-05); el resto de pares están prohibidos. */
const ALLOWED: ReadonlyArray<readonly [ApplicationStatus, ApplicationStatus]> = [
  ['GUARDADA', 'ENVIADA'],
  ['GUARDADA', 'DESCARTADA'],
  ['ENVIADA', 'ENTREVISTA'],
  ['ENVIADA', 'DESCARTADA'],
  ['ENTREVISTA', 'OFERTA'],
  ['ENTREVISTA', 'DESCARTADA'],
  ['OFERTA', 'DESCARTADA'],
  ['DESCARTADA', 'GUARDADA'],
];

const allPairs = APPLICATION_STATUSES.flatMap((from) => APPLICATION_STATUSES.map((to) => [from, to] as const));
const isAllowed = (from: ApplicationStatus, to: ApplicationStatus) =>
  ALLOWED.some(([allowedFrom, allowedTo]) => allowedFrom === from && allowedTo === to);

describe('canTransition — réplica de ApplicationStatusPolicy', () => {
  it.each(ALLOWED)('permite %s → %s', (from, to) => {
    expect(canTransition(from, to)).toBe(true);
  });

  it.each(allPairs.filter(([from, to]) => !isAllowed(from, to)))('prohíbe %s → %s', (from, to) => {
    expect(canTransition(from, to)).toBe(false);
  });

  it('la tabla cubre los 25 pares: 8 permitidos y 17 prohibidos (incluido quedarse en el mismo estado)', () => {
    expect(allPairs.filter(([from, to]) => canTransition(from, to))).toHaveLength(8);
    expect(APPLICATION_STATUSES.every((status) => !canTransition(status, status))).toBe(true);
  });

  it('allowedTargets devuelve los destinos posibles en el orden de las columnas', () => {
    expect(allowedTargets('GUARDADA')).toEqual(['ENVIADA', 'DESCARTADA']);
    expect(allowedTargets('OFERTA')).toEqual(['DESCARTADA']);
    expect(allowedTargets('DESCARTADA')).toEqual(['GUARDADA']);
  });
});
