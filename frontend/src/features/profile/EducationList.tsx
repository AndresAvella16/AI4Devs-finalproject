import { useFieldArray, type UseFormReturn } from 'react-hook-form';
import { FormField } from '../../components/FormField';
import { ListItem, ListSection } from './ListSection';
import { emptyEducation, PROFILE_LIMITS, type ProfileFormValues } from './schemas';

export function EducationList({ form }: { form: UseFormReturn<ProfileFormValues> }) {
  const {
    control,
    register,
    formState: { errors },
  } = form;
  const { fields, append, remove } = useFieldArray({ control, name: 'education' });

  return (
    <ListSection
      title="Formación"
      emptyText="Aún no has añadido ninguna formación."
      isEmpty={fields.length === 0}
      addLabel="Añadir formación"
      addTestId="add-education-button"
      onAdd={() => append(emptyEducation())}
      canAdd={fields.length < PROFILE_LIMITS.education}
      error={errors.education?.message ?? errors.education?.root?.message}
    >
      {fields.map((field, index) => {
        const number = index + 1;
        const itemErrors = errors.education?.[index];
        const id = (name: string) => `education-${index}-${name}`;
        return (
          <ListItem
            key={field.id}
            testId="education-item"
            legend={`Formación ${number}`}
            removeLabel={`Quitar formación ${number}`}
            onRemove={() => remove(index)}
          >
            <div className="grid gap-3 sm:grid-cols-[2fr_2fr_1fr]">
              <FormField
                id={id('title')}
                label="Título"
                maxLength={PROFILE_LIMITS.educationText}
                registration={register(`education.${index}.title`)}
                error={itemErrors?.title?.message}
              />
              <FormField
                id={id('institution')}
                label="Centro"
                maxLength={PROFILE_LIMITS.educationText}
                registration={register(`education.${index}.institution`)}
                error={itemErrors?.institution?.message}
              />
              <FormField
                id={id('year')}
                label="Año"
                inputMode="numeric"
                registration={register(`education.${index}.year`)}
                error={itemErrors?.year?.message}
              />
            </div>
          </ListItem>
        );
      })}
    </ListSection>
  );
}
