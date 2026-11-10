/**
 * Lo que se ve mientras llega el chunk de una página cargada con `lazy` cuando se entra directamente en ella
 * (`HydrateFallback`). Al navegar desde otra página, React Router mantiene la anterior hasta que llega la nueva.
 */
export function PageLoading() {
  return (
    <div data-testid="page-loading" aria-busy="true" className="flex flex-col gap-3">
      <span className="sr-only" role="status">
        Cargando…
      </span>
      <div className="h-8 w-48 animate-pulse rounded bg-slate-200" />
      <div className="h-40 animate-pulse rounded-lg bg-slate-200" />
    </div>
  );
}
