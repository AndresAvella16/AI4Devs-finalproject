import { z } from 'zod';

/** Política de contraseñas de la sección 2.5, igual que `PasswordPolicyValidator` del backend. */
const PASSWORD_MIN_CHARACTERS = 8;
/** BCrypt solo usa los primeros 72 bytes. */
const PASSWORD_MAX_BYTES = 72;

const characterCount = (value: string) => [...value].length;
const utf8Bytes = (value: string) => new TextEncoder().encode(value).length;

const emailField = z
  .string()
  .trim()
  .min(1, 'Introduce tu email')
  .max(254, 'El email no puede superar los 254 caracteres')
  .pipe(z.email('Introduce un email válido'));

/** `LoginRequest` (sección 4). La contraseña no pasa la política: una que no la cumpla es simplemente incorrecta. */
export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, 'Introduce tu contraseña'),
});

/** `RegisterRequest` (sección 4). */
export const registerSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, 'Introduce tu nombre')
    .max(120, 'El nombre no puede superar los 120 caracteres'),
  email: emailField,
  password: z
    .string()
    .refine((value) => characterCount(value) >= PASSWORD_MIN_CHARACTERS, {
      message: `La contraseña debe tener al menos ${PASSWORD_MIN_CHARACTERS} caracteres`,
      abort: true,
    })
    .refine((value) => /\p{L}/u.test(value) && /\p{Nd}/u.test(value), {
      message: 'La contraseña debe incluir al menos una letra y un número',
    })
    .refine((value) => utf8Bytes(value) <= PASSWORD_MAX_BYTES, {
      message: `La contraseña es demasiado larga (máximo ${PASSWORD_MAX_BYTES} bytes)`,
    }),
});

export type LoginForm = z.infer<typeof loginSchema>;
export type RegisterForm = z.infer<typeof registerSchema>;
