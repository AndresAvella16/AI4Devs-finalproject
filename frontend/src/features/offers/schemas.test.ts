import { describe, expect, it } from 'vitest';
import { countCharacters, createOfferSchema, rawTextLength, toCreateOfferRequest } from './schemas';

const valid = (rawText: string, sourceUrl = '') => createOfferSchema.safeParse({ rawText, sourceUrl }).success;

describe('countCharacters', () => {
  it('cuenta caracteres Unicode, no unidades UTF-16 (como char_length y @CodePointLength)', () => {
    expect(countCharacters('abc')).toBe(3);
    expect(countCharacters('😀😀')).toBe(2);
  });
});

describe('rawTextLength', () => {
  it('indica cuántos faltan por debajo de 200', () => {
    expect(rawTextLength('a'.repeat(150))).toEqual({ count: 150, missing: 50, excess: 0, valid: false });
  });

  it('es válido entre 200 y 20.000', () => {
    expect(rawTextLength('a'.repeat(200)).valid).toBe(true);
    expect(rawTextLength('a'.repeat(20_000)).valid).toBe(true);
  });

  it('indica cuántos sobran por encima de 20.000', () => {
    expect(rawTextLength('a'.repeat(20_003))).toEqual({ count: 20_003, missing: 0, excess: 3, valid: false });
  });
});

describe('createOfferSchema: rawText', () => {
  it.each([
    [199, false],
    [200, true],
    [20_000, true],
    [20_001, false],
  ])('%s caracteres → válido: %s', (length, expected) => {
    expect(valid('a'.repeat(length))).toBe(expected);
  });

  it('200 emojis son 200 caracteres (400 unidades UTF-16)', () => {
    expect(valid('😀'.repeat(200))).toBe(true);
    expect(valid('😀'.repeat(199))).toBe(false);
  });
});

describe('createOfferSchema: sourceUrl', () => {
  const text = 'a'.repeat(200);

  it.each([
    ['', true],
    ['https://empresa-ejemplo.com/empleo/java-junior', true],
    ['http://ejemplo.com', true],
    ['HTTPS://EJEMPLO.COM/OFERTA?id=1', true],
    ['  https://ejemplo.com  ', true],
    ['javascript:alert(1)', false],
    ['ftp://ejemplo.com/oferta', false],
    ['https://', false],
    ['https://ejemplo .com', false],
    ['ejemplo.com', false],
    [`https://ejemplo.com/${'a'.repeat(2048)}`, false],
  ])('"%s" → válida: %s', (url, expected) => {
    expect(valid(text, url)).toBe(expected);
  });
});

describe('toCreateOfferRequest', () => {
  it('una URL vacía se envía como null', () => {
    expect(toCreateOfferRequest({ rawText: 'texto', sourceUrl: '' })).toEqual({ rawText: 'texto', sourceUrl: null });
  });

  it('envía el texto tal cual y la URL sin espacios alrededor', () => {
    expect(toCreateOfferRequest({ rawText: ' texto ', sourceUrl: ' https://a.es ' })).toEqual({
      rawText: ' texto ',
      sourceUrl: 'https://a.es',
    });
  });
});
