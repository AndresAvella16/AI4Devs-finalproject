import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { Link, useNavigate } from 'react-router';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { FormField } from '../../components/FormField';
import { applyServerErrors, toGeneralError, type GeneralError } from '../../components/serverErrors';
import { TextAreaField } from '../../components/TextAreaField';
import {
  createOfferSchema,
  RAW_TEXT_MIN,
  rawTextLength,
  toCreateOfferRequest,
  type CreateOfferForm,
  type CreateOfferFormInput,
  type RawTextLength,
} from './schemas';
import { useCreateOffer } from './useOffers';

const COUNTER_ID = 'offer-raw-text-hint';

function counterText({ count, missing, excess }: RawTextLength): string {
  if (missing > 0) {
    return `${missing === 1 ? 'Falta 1 carácter' : `Faltan ${missing} caracteres`} (mínimo ${RAW_TEXT_MIN})`;
  }
  if (excess > 0) {
    return `${excess === 1 ? 'Sobra 1 carácter' : `Sobran ${excess.toLocaleString('es-ES')} caracteres`} (máximo 20.000)`;
  }
  return `${count.toLocaleString('es-ES')} / 20.000 caracteres`;
}

export function NewOfferPage() {
  const navigate = useNavigate();
  const createMutation = useCreateOffer();
  const [generalError, setGeneralError] = useState<GeneralError | null>(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    trigger,
    formState: { errors },
  } = useForm<CreateOfferFormInput, unknown, CreateOfferForm>({
    resolver: zodResolver(createOfferSchema),
    defaultValues: { rawText: '', sourceUrl: '' },
  });
  const length = rawTextLength(useWatch({ control, name: 'rawText' }));
  // La URL se valida al salir del campo (y después mientras se corrige): con el texto por debajo de 200 caracteres
  // el botón está deshabilitado, así que esperar al envío dejaría el error sin mostrar.
  const sourceUrlRegistration = register('sourceUrl', {
    onBlur: () => void trigger('sourceUrl'),
    onChange: () => {
      if (errors.sourceUrl) void trigger('sourceUrl');
    },
  });

  const onSubmit = handleSubmit((values) => {
    setGeneralError(null);
    createMutation.mutate(toCreateOfferRequest(values), {
      // Aunque la IA falle, el backend responde 201 (FALLIDA) y el detalle ofrece reintentar la extracción.
      onSuccess: (offer) => navigate(`/ofertas/${offer.id}`),
      onError: (error) => {
        if (!applyServerErrors(error, setError, ['rawText', 'sourceUrl'])) setGeneralError(toGeneralError(error));
      },
    });
  });

  const pending = createMutation.isPending;

  return (
    <section className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <Link to="/" className="text-sm font-medium text-indigo-700 underline-offset-2 hover:underline">
          <span aria-hidden="true">← </span>
          Volver a tus ofertas
        </Link>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">Nueva oferta</h1>
        <p className="mt-1 text-slate-600">
          Pega el texto completo de la oferta. La IA extraerá el puesto, la empresa y los requisitos.
        </p>
      </div>

      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4" aria-label="Nueva oferta">
        {generalError && (
          <Alert title={generalError.title} testId="offer-create-error">
            {generalError.detail}
          </Alert>
        )}
        <TextAreaField
          id="offer-raw-text"
          testId="offer-raw-text"
          label="Texto de la oferta"
          rows={14}
          registration={register('rawText')}
          error={errors.rawText?.message}
          hint={counterText(length)}
          hintTestId="offer-char-counter"
        />
        <FormField
          id="offer-source-url"
          testId="offer-source-url"
          label="URL de la oferta (opcional)"
          type="url"
          inputMode="url"
          placeholder="https://"
          hint="Solo se guarda como referencia: no se descarga."
          registration={sourceUrlRegistration}
          error={errors.sourceUrl?.message}
        />
        {pending && (
          <Alert tone="info" title="Analizando la oferta" testId="offer-create-pending">
            La IA está extrayendo los datos; puede tardar unos segundos.
          </Alert>
        )}
        <div>
          <Button
            type="submit"
            data-testid="offer-submit"
            disabled={!length.valid || pending}
            aria-describedby={length.valid ? undefined : COUNTER_ID}
          >
            {pending ? 'Analizando oferta…' : 'Analizar oferta'}
          </Button>
        </div>
      </form>
    </section>
  );
}
