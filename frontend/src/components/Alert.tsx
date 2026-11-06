import type { ReactNode } from 'react';

type Tone = 'error' | 'info';

const TONES: Record<Tone, string> = {
  error: 'border-red-300 bg-red-50 text-red-800',
  info: 'border-sky-300 bg-sky-50 text-sky-800',
};

interface AlertProps {
  tone?: Tone;
  title: string;
  children?: ReactNode;
  testId?: string;
}

/** Aviso. Los de error se anuncian al momento (`role="alert"`); los informativos, con cortesía (`role="status"`). */
export function Alert({ tone = 'error', title, children, testId }: AlertProps) {
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} data-testid={testId} className={`rounded-md border p-3 text-sm ${TONES[tone]}`}>
      <p className="font-semibold">{title}</p>
      {children && <div className="mt-1">{children}</div>}
    </div>
  );
}
