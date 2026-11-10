import { z } from 'zod';
import type { ApplicationStatus, UpdateApplicationRequest } from '../../api/applications';
import { APPLICATION_STATUS_LABELS } from '../offers/labels';

/** `UpdateApplicationRequest.notes` (sección 4): `maxLength: 5000`. */
export const NOTES_MAX = 5000;

/** Estados en los que el backend exige `appliedAt` (CHECK de `applications` y 400 en `PATCH /applications/{id}`). */
const REQUIRES_APPLIED_AT: readonly ApplicationStatus[] = ['ENVIADA', 'ENTREVISTA', 'OFERTA'];

const isoDate = z.union([z.literal(''), z.iso.date('Introduce una fecha válida')]);

/** Formulario del panel lateral. Los campos son texto (vacío = sin valor); depende del estado de la candidatura. */
export function applicationFormSchema(status: ApplicationStatus) {
  return z
    .object({
      notes: z.string().max(NOTES_MAX, `Las notas no pueden superar los ${NOTES_MAX} caracteres`),
      appliedAt: isoDate,
      followUpDate: isoDate,
    })
    .superRefine((values, context) => {
      if (REQUIRES_APPLIED_AT.includes(status) && values.appliedAt === '') {
        context.addIssue({
          code: 'custom',
          path: ['appliedAt'],
          message: `La fecha de envío es obligatoria en una candidatura ${APPLICATION_STATUS_LABELS[status]}`,
        });
      }
    });
}

export type ApplicationForm = z.infer<ReturnType<typeof applicationFormSchema>>;

/** Se envían los tres campos: uno vacío va como `null`, que en el backend borra el valor. */
export function toUpdateApplicationRequest(values: ApplicationForm, version: number): UpdateApplicationRequest {
  return {
    notes: values.notes.trim() === '' ? null : values.notes,
    appliedAt: values.appliedAt === '' ? null : values.appliedAt,
    followUpDate: values.followUpDate === '' ? null : values.followUpDate,
    version,
  };
}
