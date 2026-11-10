import { useFieldArray, type UseFormReturn } from 'react-hook-form';
import { FormField } from '../../components/FormField';
import { ListItem, ListSection } from './ListSection';
import { emptyLanguage, PROFILE_LIMITS, type ProfileFormValues } from './schemas';

export function LanguageList({ form }: { form: UseFormReturn<ProfileFormValues> }) {
  const {
    control,
    register,
    formState: { errors },
  } = form;
  const { fields, append, remove } = useFieldArray({ control, name: 'languages' });

  return (
    <ListSection
      title="Idiomas"
      emptyText="Aún no has añadido ningún idioma."
      isEmpty={fields.length === 0}
      addLabel="Añadir idioma"
      addTestId="add-language-button"
      onAdd={() => append(emptyLanguage())}
      canAdd={fields.length < PROFILE_LIMITS.languages}
      error={errors.languages?.message ?? errors.languages?.root?.message}
    >
      {fields.map((field, index) => {
        const number = index + 1;
        const itemErrors = errors.languages?.[index];
        const id = (name: string) => `languages-${index}-${name}`;
        return (
          <ListItem
            key={field.id}
            testId="language-item"
            legend={`Idioma ${number}`}
            removeLabel={`Quitar idioma ${number}`}
            onRemove={() => remove(index)}
          >
            <div className="grid gap-3 sm:grid-cols-2">
              <FormField
                id={id('language')}
                label="Idioma"
                maxLength={PROFILE_LIMITS.language}
                registration={register(`languages.${index}.language`)}
                error={itemErrors?.language?.message}
              />
              <FormField
                id={id('level')}
                label="Nivel"
                placeholder="B2, Nativo…"
                maxLength={PROFILE_LIMITS.languageLevel}
                registration={register(`languages.${index}.level`)}
                error={itemErrors?.level?.message}
              />
            </div>
          </ListItem>
        );
      })}
    </ListSection>
  );
}
