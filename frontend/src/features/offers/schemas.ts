import { z } from 'zod';
import type { CreateOfferRequest } from '../../api/offers';

/**
 * `CreateOfferRequest` (sección 4). La validación en cliente solo ayuda al usuario: la que manda es la del backend
 * (`@CodePointLength` y `@Pattern` de `CreateOfferRequest.java`), cuyos errores se llevan a los campos.
 */
export const RAW_TEXT_MIN = 200;
export const RAW_TEXT_MAX = 20_000;
export const SOURCE_URL_MAX = 2048;

/**
 * El `(?i)https?://[^\s/?#]+\S*` de la sección 4 traducido a JS: el modificador `(?i)` pasa a la bandera `i` y se
 * añaden las anclas, porque `@Pattern` exige que encaje la cadena entera.
 */
const SOURCE_URL_PATTERN = /^(?:https?:\/\/[^\s/?#]+\S*)?$/i;

/** Caracteres Unicode (code points), como `char_length` en PostgreSQL: un emoji cuenta como 1. */
export function countCharacters(value: string): number {
  return [...value].length;
}

export interface RawTextLength {
  count: number;
  /** Cuántos faltan para llegar al mínimo (0 si se llega). */
  missing: number;
  /** Cuántos sobran por encima del máximo (0 si no se pasa). */
  excess: number;
  valid: boolean;
}

export function rawTextLength(value: string): RawTextLength {
  const count = countCharacters(value);
  const missing = Math.max(0, RAW_TEXT_MIN - count);
  const excess = Math.max(0, count - RAW_TEXT_MAX);
  return { count, missing, excess, valid: missing === 0 && excess === 0 };
}

export const createOfferSchema = z.object({
  rawText: z.string().refine((value) => rawTextLength(value).valid, {
    message: `El texto debe tener entre ${RAW_TEXT_MIN} y 20.000 caracteres`,
  }),
  sourceUrl: z
    .string()
    .trim()
    .max(SOURCE_URL_MAX, `La URL no puede superar los ${SOURCE_URL_MAX} caracteres`)
    .regex(SOURCE_URL_PATTERN, 'Introduce una URL que empiece por http:// o https://'),
});

export type CreateOfferFormInput = z.input<typeof createOfferSchema>;
export type CreateOfferForm = z.output<typeof createOfferSchema>;

/** El texto se envía tal cual (el backend cuenta lo que se pega); una URL vacía se envía como `null`. */
export function toCreateOfferRequest(values: CreateOfferForm): CreateOfferRequest {
  const sourceUrl = values.sourceUrl.trim();
  return { rawText: values.rawText, sourceUrl: sourceUrl === '' ? null : sourceUrl };
}
