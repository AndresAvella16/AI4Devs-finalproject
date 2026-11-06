import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { AuthResponse } from '../../api/auth';
import { setUnauthorizedHandler } from '../../api/client';
import { clearSession, loadSession, saveSession, type Session } from '../../api/session';
import { AuthContext, type AuthContextValue, type SignOutReason } from './AuthContext';

/** `setTimeout` no admite más de 2^31 - 1 ms. */
const MAX_TIMEOUT_MS = 2 ** 31 - 1;

/**
 * Mantiene la sesión (JWT + usuario) y la cierra cuando el usuario sale, cuando el token caduca
 * o cuando la API responde 401. Al cambiar de sesión vacía la caché de TanStack Query.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [signOutReason, setSignOutReason] = useState<SignOutReason | null>(null);

  const signOut = useCallback(
    (reason: SignOutReason = 'logout') => {
      clearSession();
      queryClient.clear();
      setSession(null);
      setSignOutReason(reason);
    },
    [queryClient],
  );

  const signIn = useCallback(
    (auth: AuthResponse) => {
      queryClient.clear();
      setSession(saveSession(auth));
      setSignOutReason(null);
    },
    [queryClient],
  );

  useEffect(() => {
    setUnauthorizedHandler(() => signOut('expired'));
    return () => setUnauthorizedHandler(null);
  }, [signOut]);

  useEffect(() => {
    if (session === null) return;
    const delay = Math.min(Math.max(session.expiresAt - Date.now(), 0), MAX_TIMEOUT_MS);
    const timer = setTimeout(() => signOut('expired'), delay);
    return () => clearTimeout(timer);
  }, [session, signOut]);

  const value = useMemo<AuthContextValue>(
    () => ({ session, signOutReason, signIn, signOut }),
    [session, signOutReason, signIn, signOut],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
