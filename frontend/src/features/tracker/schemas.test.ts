import { describe, expect, it } from 'vitest';
import { applicationFormSchema, NOTES_MAX, toUpdateApplicationRequest } from './schemas';

const valid = { notes: '', appliedAt: '', followUpDate: '' };

describe('applicationFormSchema — UpdateApplicationRequest (sección 4)', () => {
  it('en Guardada todo es opcional', () => {
    expect(applicationFormSchema('GUARDADA').safeParse(valid).success).toBe(true);
  });

  it.each(['ENVIADA', 'ENTREVISTA', 'OFERTA'] as const)('en %s la fecha de envío es obligatoria', (status) => {
    const result = applicationFormSchema(status).safeParse(valid);
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['appliedAt']);
  });

  it('en Descartada la fecha de envío puede quedar vacía', () => {
    expect(applicationFormSchema('DESCARTADA').safeParse(valid).success).toBe(true);
  });

  it(`las notas admiten hasta ${NOTES_MAX} caracteres`, () => {
    expect(applicationFormSchema('GUARDADA').safeParse({ ...valid, notes: 'a'.repeat(NOTES_MAX) }).success).toBe(true);
    const result = applicationFormSchema('GUARDADA').safeParse({ ...valid, notes: 'a'.repeat(NOTES_MAX + 1) });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['notes']);
  });

  it('rechaza una fecha que no es YYYY-MM-DD', () => {
    expect(applicationFormSchema('GUARDADA').safeParse({ ...valid, followUpDate: '17/11/2026' }).success).toBe(false);
  });
});

describe('toUpdateApplicationRequest', () => {
  it('los campos vacíos se envían como null (borran el valor) junto con la versión', () => {
    expect(toUpdateApplicationRequest({ notes: '  ', appliedAt: '', followUpDate: '2026-11-17' }, 3)).toEqual({
      notes: null,
      appliedAt: null,
      followUpDate: '2026-11-17',
      version: 3,
    });
  });
});
