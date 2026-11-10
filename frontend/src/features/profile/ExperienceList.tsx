import { useFieldArray, useWatch, type UseFormReturn } from 'react-hook-form';
import { FormField } from '../../components/FormField';
import { TextAreaField } from '../../components/TextAreaField';
import { ListItem, ListSection } from './ListSection';
import { emptyExperience, PROFILE_LIMITS, type ProfileFormValues } from './schemas';

/** Experiencias, en el orden en que se muestran (el del array). "Puesto actual" deshabilita la fecha de fin. */
export function ExperienceList({ form }: { form: UseFormReturn<ProfileFormValues> }) {
  const {
    control,
    register,
    formState: { errors },
  } = form;
  const { fields, append, remove } = useFieldArray({ control, name: 'experiences' });
  const experiences = useWatch({ control, name: 'experiences' });

  return (
    <ListSection
      title="Experiencia"
      emptyText="Aún no has añadido ninguna experiencia."
      isEmpty={fields.length === 0}
      addLabel="Añadir experiencia"
      addTestId="add-experience-button"
      onAdd={() => append(emptyExperience())}
      canAdd={fields.length < PROFILE_LIMITS.experiences}
      error={errors.experiences?.message ?? errors.experiences?.root?.message}
    >
      {fields.map((field, index) => {
        const number = index + 1;
        const itemErrors = errors.experiences?.[index];
        const current = experiences[index]?.current ?? false;
        const id = (name: string) => `experiences-${index}-${name}`;
        return (
          <ListItem
            key={field.id}
            testId="experience-item"
            legend={`Experiencia ${number}`}
            removeLabel={`Quitar experiencia ${number}`}
            onRemove={() => remove(index)}
          >
            <div className="grid gap-3 sm:grid-cols-2">
              <FormField
                id={id('company')}
                testId={`experience-company-${index}`}
                label="Empresa"
                maxLength={PROFILE_LIMITS.company}
                registration={register(`experiences.${index}.company`)}
                error={itemErrors?.company?.message}
              />
              <FormField
                id={id('role')}
                label="Puesto"
                maxLength={PROFILE_LIMITS.role}
                registration={register(`experiences.${index}.role`)}
                error={itemErrors?.role?.message}
              />
              <FormField
                id={id('startDate')}
                label="Inicio"
                type="date"
                registration={register(`experiences.${index}.startDate`)}
                error={itemErrors?.startDate?.message}
              />
              <FormField
                id={id('endDate')}
                label="Fin"
                type="date"
                registration={register(`experiences.${index}.endDate`, { disabled: current })}
                error={current ? undefined : itemErrors?.endDate?.message}
              />
            </div>
            <div className="flex items-center gap-2">
              <input
                id={id('current')}
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300 focus-visible:outline-2 focus-visible:outline-indigo-600"
                {...register(`experiences.${index}.current`)}
              />
              <label htmlFor={id('current')} className="text-sm text-slate-700">
                Puesto actual
              </label>
            </div>
            <TextAreaField
              id={id('description')}
              label="Descripción"
              rows={3}
              registration={register(`experiences.${index}.description`)}
              error={itemErrors?.description?.message}
            />
          </ListItem>
        );
      })}
    </ListSection>
  );
}
