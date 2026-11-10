import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import type { ApplicationResponse } from '../../api/applications';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { FormField } from '../../components/FormField';
import { applyServerErrors, toGeneralError, type GeneralError } from '../../components/serverErrors';
import { TextAreaField } from '../../components/TextAreaField';
import { APPLICATION_STATUS_LABELS } from '../offers/labels';
import { cardTitle } from './labels';
import { applicationFormSchema, NOTES_MAX, toUpdateApplicationRequest, type ApplicationForm } from './schemas';
import { useUpdateApplication } from './useApplications';

const FIELDS = ['notes', 'appliedAt', 'followUpDate'] as const;

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])';

interface ApplicationDrawerProps {
  application: ApplicationResponse;
  onClose: () => void;
}

/**
 * Panel lateral de una candidatura: notas, fecha de envío y fecha de seguimiento → `PATCH /applications/{id}` con la
 * `version` que se ve. Es un diálogo modal: atrapa el foco, se cierra con Escape y devuelve el foco a la tarjeta (eso
 * lo hace quien lo abre).
 */
export function ApplicationDrawer({ application, onClose }: ApplicationDrawerProps) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const [generalError, setGeneralError] = useState<GeneralError | null>(null);
  const update = useUpdateApplication(application.id);
  const schema = useMemo(() => applicationFormSchema(application.status), [application.status]);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm<ApplicationForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      notes: application.notes ?? '',
      appliedAt: application.appliedAt ?? '',
      followUpDate: application.followUpDate ?? '',
    },
  });

  useEffect(() => setFocus('notes'), [setFocus]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab' || !panelRef.current) return;
      const focusable = [...panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)];
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const onSubmit = handleSubmit((values) => {
    setGeneralError(null);
    update.mutate(toUpdateApplicationRequest(values, application.version), {
      onSuccess: onClose,
      onError: (error) => {
        if (!applyServerErrors(error, setError, FIELDS)) setGeneralError(toGeneralError(error));
      },
    });
  });

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-slate-900/40">
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        data-testid="application-drawer"
        className="flex h-full w-full max-w-md flex-col gap-4 overflow-y-auto bg-white p-6 shadow-xl"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id={titleId} className="text-lg font-bold text-slate-900">
              {cardTitle(application)}
            </h2>
            <p className="text-sm text-slate-600">
              {application.company ?? 'Empresa no indicada'} · {APPLICATION_STATUS_LABELS[application.status]}
            </p>
          </div>
          <Button variant="secondary" onClick={onClose} aria-label="Cerrar panel">
            ×
          </Button>
        </div>
        <Link
          to={`/ofertas/${application.offerId}`}
          className="w-fit rounded text-sm font-medium text-indigo-700 underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >
          Ver la oferta
        </Link>

        <form onSubmit={(event) => void onSubmit(event)} noValidate className="flex flex-col gap-4">
          {generalError && <Alert title={generalError.title}>{generalError.detail}</Alert>}
          <TextAreaField
            id="application-notes"
            label="Notas"
            rows={6}
            testId="application-notes"
            registration={register('notes')}
            error={errors.notes?.message}
            hint={`Hasta ${NOTES_MAX.toLocaleString('es-ES')} caracteres`}
          />
          <FormField
            id="application-applied-at"
            type="date"
            label="Fecha de envío"
            testId="application-applied-at"
            registration={register('appliedAt')}
            error={errors.appliedAt?.message}
          />
          <FormField
            id="application-follow-up"
            type="date"
            label="Fecha de seguimiento"
            testId="application-follow-up-date"
            registration={register('followUpDate')}
            error={errors.followUpDate?.message}
          />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={update.isPending} data-testid="application-save">
              {update.isPending ? 'Guardando…' : 'Guardar'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
