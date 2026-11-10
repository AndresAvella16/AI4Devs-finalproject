import { useCallback, useRef, useState } from 'react';
import { Link } from 'react-router';
import type { ApplicationResponse } from '../../api/applications';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { toGeneralError } from '../../components/serverErrors';
import { ApplicationDrawer } from './ApplicationDrawer';
import { KanbanBoard } from './KanbanBoard';
import { MobileBoard } from './MobileBoard';
import { useApplications } from './useApplications';
import { useIsDesktop } from './useIsDesktop';

const linkButton =
  'inline-block rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600';

/** Tablero (`/tablero`, F5 / HU-05): las candidaturas del usuario en 5 columnas. */
export function BoardPage() {
  const applications = useApplications();
  const isDesktop = useIsDesktop();
  const [openId, setOpenId] = useState<string | null>(null);
  const trigger = useRef<HTMLElement | null>(null);

  const open = useCallback((application: ApplicationResponse, element: HTMLElement) => {
    trigger.current = element;
    setOpenId(application.id);
  }, []);
  const close = useCallback(() => {
    setOpenId(null);
    trigger.current?.focus();
  }, []);

  const opened = applications.data?.find((card) => card.id === openId);

  return (
    <section className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Tablero</h1>
        <p className="text-sm text-slate-600">Arrastra cada candidatura a su columna según avanza.</p>
      </div>

      {applications.isPending ? (
        <div data-testid="board-loading" aria-busy="true" className="grid grid-cols-1 gap-3 md:grid-cols-5">
          <span className="sr-only" role="status">
            Cargando tus candidaturas…
          </span>
          {[0, 1, 2, 3, 4].map((key) => (
            <div key={key} className="h-48 animate-pulse rounded-lg bg-slate-200" />
          ))}
        </div>
      ) : applications.isError ? (
        <div data-testid="board-error" className="flex flex-col items-start gap-3">
          <Alert title="No se ha podido cargar el tablero">{toGeneralError(applications.error).detail}</Alert>
          <Button variant="secondary" onClick={() => void applications.refetch()} disabled={applications.isFetching}>
            Reintentar
          </Button>
        </div>
      ) : applications.data.length === 0 ? (
        <div
          data-testid="board-empty"
          className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-slate-300 bg-white p-10 text-center"
        >
          <p className="font-medium text-slate-800">Aún no tienes candidaturas</p>
          <Link to="/ofertas/nueva" className={linkButton}>
            Importa una oferta
          </Link>
        </div>
      ) : isDesktop ? (
        <KanbanBoard cards={applications.data} onOpen={open} />
      ) : (
        <MobileBoard cards={applications.data} onOpen={open} />
      )}

      {opened && <ApplicationDrawer key={opened.id} application={opened} onClose={close} />}
    </section>
  );
}
