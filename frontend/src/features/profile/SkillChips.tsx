import { useFieldArray, useWatch, type UseFormReturn } from 'react-hook-form';
import { SKILL_LEVELS } from '../../api/profile';
import { Button } from '../../components/Button';
import { SKILL_LEVEL_LABELS } from './labels';
import { emptySkill, PROFILE_LIMITS, type ProfileFormValues } from './schemas';

const chipInput =
  'rounded border bg-white px-1.5 py-0.5 text-sm text-slate-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500';

/**
 * Habilidades como *chips*: cada uno lleva el nombre, el nivel y los años, editables, y un botón para quitarlo. Las
 * etiquetas son solo para lectores de pantalla ("Habilidad 3", "Nivel de la habilidad 3"…).
 */
export function SkillChips({ form }: { form: UseFormReturn<ProfileFormValues> }) {
  const {
    control,
    register,
    formState: { errors },
  } = form;
  const { fields, append, remove } = useFieldArray({ control, name: 'skills' });
  const skills = useWatch({ control, name: 'skills' });
  const listError = errors.skills?.message ?? errors.skills?.root?.message;

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="text-lg font-semibold text-slate-900">Habilidades</legend>
      {fields.length === 0 && <p className="text-sm text-slate-500">Aún no has añadido ninguna habilidad.</p>}
      <ul className="flex flex-wrap gap-2" aria-label="Habilidades">
        {fields.map((field, index) => {
          const number = index + 1;
          const name = skills[index]?.name.trim() || `habilidad ${number}`;
          const nameError = errors.skills?.[index]?.name?.message;
          const yearsError = errors.skills?.[index]?.years?.message;
          const errorId = `skills-${index}-error`;
          return (
            <li
              key={field.id}
              data-testid="skill-chip"
              className={`flex flex-col gap-1 rounded-2xl border px-3 py-1.5 ${
                nameError || yearsError ? 'border-red-400 bg-red-50' : 'border-indigo-200 bg-indigo-50'
              }`}
            >
              <div className="flex flex-wrap items-center gap-1.5">
                <label htmlFor={`skills-${index}-name`} className="sr-only">
                  Habilidad {number}
                </label>
                <input
                  id={`skills-${index}-name`}
                  data-testid={`skill-name-${index}`}
                  size={12}
                  maxLength={PROFILE_LIMITS.skillName}
                  aria-invalid={nameError ? true : undefined}
                  aria-describedby={nameError ? errorId : undefined}
                  className={`${chipInput} font-medium ${nameError ? 'border-red-500' : 'border-transparent'}`}
                  {...register(`skills.${index}.name`)}
                />
                <label htmlFor={`skills-${index}-level`} className="sr-only">
                  Nivel de la habilidad {number}
                </label>
                <select id={`skills-${index}-level`} className={`${chipInput} border-transparent`} {...register(`skills.${index}.level`)}>
                  <option value="">Sin nivel</option>
                  {SKILL_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {SKILL_LEVEL_LABELS[level]}
                    </option>
                  ))}
                </select>
                <label htmlFor={`skills-${index}-years`} className="sr-only">
                  Años de la habilidad {number}
                </label>
                <input
                  id={`skills-${index}-years`}
                  type="number"
                  min={0}
                  max={PROFILE_LIMITS.years}
                  placeholder="años"
                  aria-invalid={yearsError ? true : undefined}
                  aria-describedby={yearsError ? errorId : undefined}
                  className={`${chipInput} w-16 ${yearsError ? 'border-red-500' : 'border-transparent'}`}
                  {...register(`skills.${index}.years`)}
                />
                <button
                  type="button"
                  aria-label={`Quitar ${name}`}
                  onClick={() => remove(index)}
                  className="rounded-full px-1.5 text-lg leading-none text-slate-600 hover:bg-white hover:text-red-700 focus-visible:outline-2 focus-visible:outline-indigo-600"
                >
                  <span aria-hidden="true">×</span>
                </button>
              </div>
              {(nameError || yearsError) && (
                <p id={errorId} className="text-xs text-red-700">
                  {[nameError, yearsError].filter(Boolean).join(' · ')}
                </p>
              )}
            </li>
          );
        })}
      </ul>
      {listError && <p className="text-sm text-red-700">{listError}</p>}
      <div>
        <Button
          variant="secondary"
          data-testid="add-skill-button"
          onClick={() => append(emptySkill())}
          disabled={fields.length >= PROFILE_LIMITS.skills}
        >
          Añadir habilidad
        </Button>
      </div>
    </fieldset>
  );
}
