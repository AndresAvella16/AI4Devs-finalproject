import { useSyncExternalStore } from 'react';

/** A partir de este ancho el tablero se ve en columnas (`md` de Tailwind); por debajo, en pestañas. */
const DESKTOP_QUERY = '(min-width: 768px)';

function mediaQuery(): MediaQueryList | null {
  return typeof window.matchMedia === 'function' ? window.matchMedia(DESKTOP_QUERY) : null;
}

function subscribe(onChange: () => void): () => void {
  const query = mediaQuery();
  query?.addEventListener('change', onChange);
  return () => query?.removeEventListener('change', onChange);
}

/** `true` en pantallas de 768 px o más. Sin `matchMedia` (p. ej. jsdom) se toma escritorio. */
export function useIsDesktop(): boolean {
  return useSyncExternalStore(subscribe, () => mediaQuery()?.matches ?? true);
}
