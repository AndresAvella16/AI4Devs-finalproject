import { createContext } from 'react';
import type { AuthResponse } from '../../api/auth';
import type { Session } from '../../api/session';

/** Por qué terminó la última sesión: lo usa `ProtectedRoute` para decidir si recordar la ruta de origen. */
export type SignOutReason = 'logout' | 'expired';

export interface AuthContextValue {
  session: Session | null;
  /** Motivo del último cierre de sesión en esta pestaña, o `null` si no se ha cerrado ninguna. */
  signOutReason: SignOutReason | null;
  signIn: (auth: AuthResponse) => void;
  signOut: (reason?: SignOutReason) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
