import { describe, expect, it } from 'vitest';
import { CV_FILE_ERROR, CV_MAX_BYTES, validateCvFile } from './cvFile';

function file(size: number, name = 'cv.pdf', type = 'application/pdf'): File {
  return new File([new Uint8Array(size)], name, { type });
}

describe('validateCvFile — comprobación en el cliente antes de enviar el CV (HU-01)', () => {
  it('acepta un PDF de hasta 5 MB', () => {
    expect(validateCvFile(file(1024))).toBeNull();
    expect(validateCvFile(file(CV_MAX_BYTES))).toBeNull();
  });

  it('rechaza un PDF de más de 5 MB', () => {
    expect(validateCvFile(file(CV_MAX_BYTES + 1))).toBe(CV_FILE_ERROR);
  });

  it('rechaza lo que no es un PDF, aunque se llame .pdf', () => {
    expect(validateCvFile(file(100, 'cv.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'))).toBe(
      CV_FILE_ERROR,
    );
    expect(validateCvFile(file(100, 'foto.png', 'image/png'))).toBe(CV_FILE_ERROR);
    expect(validateCvFile(file(100, 'cv.pdf', ''))).toBe(CV_FILE_ERROR);
  });

  it('rechaza un fichero vacío', () => {
    expect(validateCvFile(file(0))).toBe('El fichero está vacío.');
  });

  it('el mensaje es el de la HU-01', () => {
    expect(CV_FILE_ERROR).toBe('Solo se admiten PDF de hasta 5 MB.');
  });
});
