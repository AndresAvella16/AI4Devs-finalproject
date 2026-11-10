import { APPLICATION_STATUSES, type ApplicationStatus } from '../../api/applications';

/**
 * Réplica en cliente de `ApplicationStatusPolicy` (HU-05), solo para resaltar las columnas no válidas mientras se
 * arrastra y para el menú "Mover a…". Quien decide es el backend: si responde 409, el movimiento se deshace.
 *
 * Solo se avanza al estado siguiente (GUARDADA → ENVIADA → ENTREVISTA → OFERTA); desde cualquier estado distinto de
 * DESCARTADA se puede descartar; DESCARTADA → GUARDADA reabre. Quedarse en el mismo estado no es una transición.
 */
const NEXT: Record<ApplicationStatus, readonly ApplicationStatus[]> = {
  GUARDADA: ['ENVIADA', 'DESCARTADA'],
  ENVIADA: ['ENTREVISTA', 'DESCARTADA'],
  ENTREVISTA: ['OFERTA', 'DESCARTADA'],
  OFERTA: ['DESCARTADA'],
  DESCARTADA: ['GUARDADA'],
};

export function canTransition(from: ApplicationStatus, to: ApplicationStatus): boolean {
  return NEXT[from].includes(to);
}

/** Columnas a las que se puede mover una tarjeta, en el orden del tablero. */
export function allowedTargets(from: ApplicationStatus): ApplicationStatus[] {
  return APPLICATION_STATUSES.filter((to) => canTransition(from, to));
}
