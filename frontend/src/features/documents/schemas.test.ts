import { describe, expect, it } from 'vitest';
import { CONTENT_MAX, documentVersionSchema } from './schemas';

function errorOf(contentMarkdown: string): string | undefined {
  const result = documentVersionSchema.safeParse({ contentMarkdown });
  return result.success ? undefined : result.error.issues[0]?.message;
}

describe('documentVersionSchema (CreateDocumentVersionRequest, sección 4)', () => {
  it('acepta un texto con contenido, también de exactamente 20.000 caracteres', () => {
    expect(errorOf('Estimado equipo:')).toBeUndefined();
    expect(errorOf('a'.repeat(CONTENT_MAX))).toBeUndefined();
  });

  it('rechaza un texto vacío o con solo espacios (@NotBlank)', () => {
    expect(errorOf('')).toBe('Escribe el contenido del documento.');
    expect(errorOf('  \n\t ')).toBe('Escribe el contenido del documento.');
  });

  it('rechaza más de 20.000 caracteres (@Size cuenta unidades UTF-16, como String.length)', () => {
    expect(CONTENT_MAX).toBe(20_000);
    expect(errorOf('a'.repeat(CONTENT_MAX + 1))).toBe('El documento no puede superar los 20.000 caracteres.');
    // Un emoji son 2 unidades UTF-16: 10.000 emojis + 1 letra = 20.001 para el backend.
    expect(errorOf('😀'.repeat(10_000) + 'a')).toBe('El documento no puede superar los 20.000 caracteres.');
  });

  it('envía el texto tal cual, sin recortar los espacios', () => {
    expect(documentVersionSchema.parse({ contentMarkdown: '  Hola\n' })).toEqual({ contentMarkdown: '  Hola\n' });
  });
});
