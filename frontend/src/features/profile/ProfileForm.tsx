import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { ApiError } from '../../api/ApiError';
import type { SavedProfile } from '../../api/profile';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { FormField } from '../../components/FormField';
import { toGeneralError, type GeneralError } from '../../components/serverErrors';
import { TextAreaField } from '../../components/TextAreaField';
import { EducationList } from './EducationList';
import { ExperienceList } from './ExperienceList';
import { LanguageList } from './LanguageList';
import { profileFormSchema, PROFILE_LIMITS, toFormPath, toProfileRequest, type ProfileFormValues } from './schemas';
import { SkillChips } from './SkillChips';
import { useSaveProfile } from './useProfile';

interface ProfileFormProps {
  /** Valores iniciales (borrador, perfil guardado o vacío). Para cambiarlos, se vuelve a montar con otra `key`. */
  defaultValues: ProfileFormValues;
  onSaved: (saved: SavedProfile) => void;
}

/** Formulario del perfil (`PUT /profile`). Los errores del backend que se asocian a un campo se muestran en él. */
export function ProfileForm({ defaultValues, onSaved }: ProfileFormProps) {
  const save = useSaveProfile();
  const [generalError, setGeneralError] = useState<GeneralError | null>(null);
  const form = useForm<ProfileFormValues>({ resolver: zodResolver(profileFormSchema), defaultValues });
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = form;

  const onSubmit = handleSubmit((values) => {
    setGeneralError(null);
    save.mutate(toProfileRequest(values), {
      onSuccess: onSaved,
      onError: (error) => {
        let mapped = false;
        if (error instanceof ApiError) {
          for (const { field, message } of error.errors) {
            const path = toFormPath(field);
            if (path === null) continue;
            setError(path, { type: 'server', message }, { shouldFocus: !mapped });
            mapped = true;
          }
        }
        if (!mapped) setGeneralError(toGeneralError(error));
      },
    });
  });

  return (
    <form noValidate onSubmit={onSubmit} aria-label="Perfil profesional" data-testid="profile-form" className="flex flex-col gap-8">
      <section className="flex flex-col gap-4" aria-label="Datos generales">
        <h2 className="text-lg font-semibold text-slate-900">Datos generales</h2>
        <FormField
          id="profile-headline"
          testId="profile-headline"
          label="Titular"
          placeholder="Desarrolladora Web Junior · Java"
          maxLength={PROFILE_LIMITS.headline}
          registration={register('headline')}
          error={errors.headline?.message}
        />
        <TextAreaField
          id="profile-summary"
          testId="profile-summary"
          label="Resumen"
          rows={4}
          registration={register('summary')}
          error={errors.summary?.message}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            id="profile-years"
            testId="profile-years"
            label="Años de experiencia"
            type="number"
            min={0}
            max={PROFILE_LIMITS.years}
            registration={register('yearsExperience')}
            error={errors.yearsExperience?.message}
          />
          <FormField
            id="profile-location"
            testId="profile-location"
            label="Ubicación"
            maxLength={PROFILE_LIMITS.location}
            registration={register('location')}
            error={errors.location?.message}
          />
        </div>
      </section>

      <SkillChips form={form} />
      <ExperienceList form={form} />
      <EducationList form={form} />
      <LanguageList form={form} />

      {generalError && (
        <Alert title={generalError.title} testId="profile-save-error">
          {generalError.detail}
        </Alert>
      )}
      <div>
        <Button type="submit" data-testid="profile-save-button" disabled={save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar perfil'}
        </Button>
      </div>
    </form>
  );
}
