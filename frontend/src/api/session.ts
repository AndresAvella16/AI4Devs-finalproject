import { z } from 'zod';
import type { AuthResponse, AuthUser } from './auth';

/**
 * Sesión guardada en `localStorage` para que sobreviva a las recargas y se comparta entre pestañas.
 * Deuda técnica conocida (readme, sección 2.6): un XSS podría leer el token.
 */
export const SESSION_STORAGE_KEY = 'jobpilot.session';

export interface Session {
  accessToken: string;
  /** Instante de caducidad del token (ms desde epoch), calculado con `expiresIn`. */
  expiresAt: number;
  user: AuthUser;
}

const sessionSchema = z.object({
  accessToken: z.string().min(1),
  expiresAt: z.number(),
  user: z.object({ id: z.string(), email: z.string(), fullName: z.string() }),
});

export function saveSession(auth: AuthResponse, now: number = Date.now()): Session {
  const session: Session = {
    accessToken: auth.accessToken,
    expiresAt: now + auth.expiresIn * 1000,
    user: { id: auth.user.id, email: auth.user.email, fullName: auth.user.fullName },
  };
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  return session;
}

/** Devuelve la sesión guardada, o `null` si no hay, no es válida o ha caducado (en esos casos la borra). */
export function loadSession(now: number = Date.now()): Session | null {
  const raw = localStorage.getItem(SESSION_STORAGE_KEY);
  if (raw === null) return null;
  const session = parse(raw);
  if (session === null || session.expiresAt <= now) {
    clearSession();
    return null;
  }
  return session;
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_STORAGE_KEY);
}

function parse(raw: string): Session | null {
  try {
    const result = sessionSchema.safeParse(JSON.parse(raw));
    return result.success ? result.data : null;
  } catch {
    return null;
  }
}
