import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '../../api/ApiError';
import type { SavedProfile } from '../../api/profile';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { formatWait } from '../../components/formatWait';
import { toGeneralError } from '../../components/serverErrors';
import { useToast } from '../../components/useToast';
import { PageLoading } from '../../app/PageLoading';
import { CvDropzone } from './CvDropzone';
import { validateCvFile } from './cvFile';
import { ProfileForm } from './ProfileForm';
import { emptyProfileForm, toProfileForm, type ProfileFormValues } from './schemas';
import { useProfile, useUploadCv } from './useProfile';

/** Lo que muestra el formulario cuando no es el perfil guardado: un borrador de la IA o el perfil hecho a mano. */
interface FormSource {
  /** Cambia en cada borrador para volver a montar el formulario con sus valores. */
  key: number;
  values: ProfileFormValues;
  /** Nombre del PDF del que sale el borrador; `null` si se rellena a mano. */
  draftFileName: string | null;
}

/** Errores de la subida que son del fichero (no llegan a la IA): 400 en `file` y 415 `invalid-cv-file`. */
function isFileError(error: unknown): error is ApiError {
  return error instanceof ApiError && (error.status === 400 || error.status === 415);
}

function fileErrorMessage(error: ApiError): string {
  return error.errors.find((item) => item.field === 'file')?.message ?? error.detail ?? error.title;
}

/**
 * `/perfil` (HU-01): onboarding y edición del perfil. Sin perfil, se sube el CV (o se rellena a mano) y se revisa el
 * borrador de la IA antes de guardarlo. Con perfil, se edita; volver a subir un CV pide confirmación antes de
 * sustituir el formulario por el borrador nuevo.
 */
export function ProfilePage() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const profileQuery = useProfile();
  const upload = useUploadCv();
  const [source, setSource] = useState<FormSource | null>(null);
  const [nextKey, setNextKey] = useState(1);
  const [fileError, setFileError] = useState<string | null>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [lastFile, setLastFile] = useState<File | null>(null);

  if (profileQuery.isPending) return <PageLoading />;
  if (profileQuery.isError) {
    return (
      <section className="flex flex-col items-start gap-3" data-testid="profile-load-error">
        <h1 className="text-2xl font-bold text-slate-900">Tu perfil</h1>
        <Alert title="No se ha podido cargar tu perfil">{toGeneralError(profileQuery.error).detail}</Alert>
        <Button variant="secondary" onClick={() => void profileQuery.refetch()} disabled={profileQuery.isFetching}>
          Reintentar
        </Button>
      </section>
    );
  }

  const profile = profileQuery.data;
  const shownValues = source?.values ?? (profile ? toProfileForm(profile) : null);
  const formKey = source?.key ?? 0;

  const startUpload = (file: File) => {
    setLastFile(file);
    setFileError(null);
    upload.mutate(file, {
      onSuccess: (draft) => {
        setSource({ key: nextKey, values: toProfileForm(draft), draftFileName: draft.cvFileName });
        setNextKey((key) => key + 1);
      },
      onError: (error) => {
        if (isFileError(error)) setFileError(fileErrorMessage(error));
      },
    });
  };

  const onFile = (file: File) => {
    upload.reset();
    setPendingFile(null);
    const invalid = validateCvFile(file);
    setFileError(invalid);
    if (invalid !== null) return;
    // Hay un formulario a la vista (perfil guardado, borrador o a mano): se confirma antes de sustituirlo.
    if (shownValues !== null) setPendingFile(file);
    else startUpload(file);
  };

  const fillManually = () => {
    upload.reset();
    setFileError(null);
    setSource({ key: nextKey, values: emptyProfileForm(), draftFileName: null });
    setNextKey((key) => key + 1);
  };

  const onSaved = ({ created }: SavedProfile) => {
    if (created) {
      showToast({ title: 'Perfil creado', detail: 'Ya puedes calcular tu encaje con las ofertas.' });
      void navigate('/');
      return;
    }
    setSource((current) => current && { ...current, draftFileName: null });
    showToast({ title: 'Perfil guardado' });
  };

  const uploadError = upload.isError && !isFileError(upload.error) ? upload.error : null;
  const withoutText = uploadError instanceof ApiError && uploadError.slug === 'cv-without-text';
  const retryAfter = uploadError instanceof ApiError ? uploadError.retryAfter : null;

  return (
    <section data-testid="profile-page" className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">{profile ? 'Tu perfil' : 'Crea tu perfil'}</h1>
        <p className="mt-1 text-slate-600">
          {profile
            ? 'Revisa y actualiza tu perfil. Puedes volver a subir tu CV para rellenarlo de nuevo.'
            : 'Sube tu CV en PDF y la IA rellenará tu perfil. Podrás revisarlo y editarlo antes de guardarlo.'}
        </p>
        {profile?.cvFileName && !source?.draftFileName && (
          <p className="mt-1 text-sm text-slate-500" data-testid="profile-cv-file">
            CV: {profile.cvFileName}
          </p>
        )}
      </div>

      <CvDropzone onFile={onFile} disabled={upload.isPending} />

      {fileError && (
        <Alert title="Fichero no válido" testId="cv-file-error">
          {fileError}
        </Alert>
      )}

      {pendingFile && (
        <div data-testid="cv-replace-confirm" className="flex flex-col gap-3">
          <Alert tone="info" title="¿Reemplazar los datos del formulario?">
            Se rellenará con lo que extraiga la IA de «{pendingFile.name}». Los cambios que no hayas guardado se
            perderán; tu perfil no cambia hasta que lo guardes.
          </Alert>
          <div className="flex gap-2">
            <Button
              onClick={() => {
                const file = pendingFile;
                setPendingFile(null);
                startUpload(file);
              }}
            >
              Sí, reemplazar
            </Button>
            <Button variant="secondary" onClick={() => setPendingFile(null)}>
              Cancelar
            </Button>
          </div>
        </div>
      )}

      {upload.isPending && (
        <Alert tone="info" title="Analizando tu CV" testId="cv-upload-pending">
          La IA está extrayendo tus datos; puede tardar unos segundos.
        </Alert>
      )}

      {withoutText ? (
        <div data-testid="cv-without-text" className="flex flex-col items-start gap-3">
          <Alert tone="info" title="No hemos podido leer texto en tu PDF">
            Puede que sea un documento escaneado. Sube un PDF con texto (no escaneado) o rellena tu perfil a mano.
          </Alert>
          <Button variant="secondary" onClick={fillManually}>
            Rellenar a mano
          </Button>
        </div>
      ) : (
        uploadError && (
          <div data-testid="cv-upload-error" className="flex flex-col items-start gap-3">
            <Alert title={toGeneralError(uploadError).title}>
              {toGeneralError(uploadError).detail && <p>{toGeneralError(uploadError).detail}</p>}
              {retryAfter !== null && retryAfter > 0 && <p>Vuelve a intentarlo dentro de {formatWait(retryAfter)}.</p>}
              <p>También puedes rellenar tu perfil a mano.</p>
            </Alert>
            <Button variant="secondary" disabled={lastFile === null} onClick={() => lastFile && startUpload(lastFile)}>
              Reintentar
            </Button>
          </div>
        )
      )}

      {shownValues === null
        ? !withoutText && (
            <div>
              <Button variant="secondary" data-testid="profile-manual-button" onClick={fillManually}>
                Rellenar a mano
              </Button>
            </div>
          )
        : (
            <>
              {source?.draftFileName && (
                <Alert tone="info" title="Revisa tu perfil" testId="cv-draft-notice">
                  Datos extraídos de «{source.draftFileName}». Revísalos antes de guardar.
                </Alert>
              )}
              <ProfileForm key={formKey} defaultValues={shownValues} onSaved={onSaved} />
            </>
          )}
    </section>
  );
}
