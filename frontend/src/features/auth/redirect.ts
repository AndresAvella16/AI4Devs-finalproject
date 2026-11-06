/** Estado de navegación que deja `ProtectedRoute` al mandar a /login. */
export interface LoginRedirectState {
  from: { pathname: string; search?: string; hash?: string };
}

/** Ruta a la que volver tras iniciar sesión: la que se pidió, o el inicio. Nunca de vuelta a /login o /registro. */
export function redirectTarget(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('from' in state)) return '/';
  const from: unknown = state.from;
  if (typeof from !== 'object' || from === null || !('pathname' in from) || typeof from.pathname !== 'string') {
    return '/';
  }
  if (from.pathname === '/login' || from.pathname === '/registro') return '/';
  const search = 'search' in from && typeof from.search === 'string' ? from.search : '';
  const hash = 'hash' in from && typeof from.hash === 'string' ? from.hash : '';
  return `${from.pathname}${search}${hash}`;
}
