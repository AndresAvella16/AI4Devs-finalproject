import type { ReactNode } from 'react';

/** Marco común de las pantallas de login y registro. */
export function AuthCard({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="mx-auto mt-12 w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
      <h1 className="mb-6 text-2xl font-bold text-slate-900">{title}</h1>
      {children}
      <p className="mt-6 text-center text-sm text-slate-600">{footer}</p>
    </div>
  );
}
