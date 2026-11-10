import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ToastContext, type ToastMessage, type ToastTone } from './ToastContext';

/** Tiempo que se ve un aviso antes de cerrarse solo. */
export const TOAST_DURATION_MS = 6000;

interface Toast extends ToastMessage {
  id: number;
}

const TONES: Record<ToastTone, string> = {
  error: 'border-red-300 bg-red-50 text-red-900',
  info: 'border-sky-300 bg-sky-50 text-sky-900',
};

/**
 * Avisos efímeros (*toasts*) abajo a la derecha. Los de error se anuncian al momento (`role="alert"`); los
 * informativos, con cortesía (`role="status"`). Se cierran solos o con el botón.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(0);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: number) => {
    clearTimeout(timers.current.get(id));
    timers.current.delete(id);
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (message: ToastMessage) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { ...message, id }]);
      timers.current.set(
        id,
        setTimeout(() => dismiss(id), TOAST_DURATION_MS),
      );
    },
    [dismiss],
  );

  useEffect(() => {
    const pending = timers.current;
    return () => pending.forEach((timer) => clearTimeout(timer));
  }, []);

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-4 bottom-4 z-50 flex flex-col items-end gap-2 sm:left-auto">
        {toasts.map(({ id, tone = 'error', title, detail }) => (
          <div
            key={id}
            role={tone === 'error' ? 'alert' : 'status'}
            data-testid="toast"
            className={`pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-md border p-3 text-sm shadow-lg ${TONES[tone]}`}
          >
            <div className="flex-1">
              <p className="font-semibold">{title}</p>
              {detail && <p className="mt-1">{detail}</p>}
            </div>
            <button
              type="button"
              onClick={() => dismiss(id)}
              className="rounded px-1 font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
              aria-label="Cerrar aviso"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
