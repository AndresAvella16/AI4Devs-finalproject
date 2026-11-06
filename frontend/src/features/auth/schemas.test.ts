import { describe, expect, it } from 'vitest';
import type { z } from 'zod';
import { loginSchema, registerSchema } from './schemas';

const valid = { email: 'ana@example.com', password: 'Secreto123', fullName: 'Ana Pérez' };

function messages(result: z.ZodSafeParseResult<unknown>): string[] {
  return result.success ? [] : result.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`);
}

describe('registerSchema (RegisterRequest, sección 4)', () => {
  it('acepta un registro válido y recorta espacios', () => {
    const result = registerSchema.safeParse({ ...valid, email: '  ana@example.com ', fullName: ' Ana Pérez ' });

    expect(result.success).toBe(true);
    expect(result.data).toEqual(valid);
  });

  it.each([
    ['de 7 caracteres', 'Abcde12', 'password: La contraseña debe tener al menos 8 caracteres'],
    ['sin número', 'Abcdefgh', 'password: La contraseña debe incluir al menos una letra y un número'],
    ['sin letra', '12345678', 'password: La contraseña debe incluir al menos una letra y un número'],
    ['de más de 72 bytes UTF-8', 'ñ'.repeat(36) + '1', 'password: La contraseña es demasiado larga (máximo 72 bytes)'],
  ])('rechaza una contraseña %s', (_caso, password, expected) => {
    expect(messages(registerSchema.safeParse({ ...valid, password }))).toContain(expected);
  });

  it('cuenta caracteres, no bytes, para el mínimo: 8 caracteres multibyte con letra y número', () => {
    expect(registerSchema.safeParse({ ...valid, password: 'ñññññññ1' }).success).toBe(true);
  });

  it('acepta exactamente 72 bytes', () => {
    expect(registerSchema.safeParse({ ...valid, password: 'a'.repeat(71) + '1' }).success).toBe(true);
  });

  it.each([
    ['vacío', '', 'email: Introduce tu email'],
    ['sin formato de email', 'ana@', 'email: Introduce un email válido'],
    ['de más de 254 caracteres', 'a'.repeat(250) + '@x.es', 'email: El email no puede superar los 254 caracteres'],
  ])('rechaza un email %s', (_caso, email, expected) => {
    expect(messages(registerSchema.safeParse({ ...valid, email }))).toContain(expected);
  });

  it.each([
    ['vacío', '   ', 'fullName: Introduce tu nombre'],
    ['de más de 120 caracteres', 'a'.repeat(121), 'fullName: El nombre no puede superar los 120 caracteres'],
  ])('rechaza un nombre %s', (_caso, fullName, expected) => {
    expect(messages(registerSchema.safeParse({ ...valid, fullName }))).toContain(expected);
  });
});

describe('loginSchema (LoginRequest, sección 4)', () => {
  it('no aplica la política de contraseñas al login', () => {
    expect(loginSchema.safeParse({ email: 'ana@example.com', password: 'x' }).success).toBe(true);
  });

  it('exige un email válido y la contraseña', () => {
    expect(messages(loginSchema.safeParse({ email: 'nope', password: '' }))).toEqual([
      'email: Introduce un email válido',
      'password: Introduce tu contraseña',
    ]);
  });
});
