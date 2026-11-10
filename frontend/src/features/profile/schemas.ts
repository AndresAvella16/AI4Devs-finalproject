import type { Path } from 'react-hook-form';
import { z } from 'zod';
import type { ProfileRequest, SkillLevel } from '../../api/profile';

/**
 * Formulario del perfil: refleja `ProfileRequest` (sección 4 y `ProfileRequest.java`). Los campos numéricos y las
 * fechas son texto mientras se editan (lo que dan los `<input>`); `toProfileRequest` los convierte al enviar. La
 * validación del cliente solo ayuda al usuario: la que manda es la del backend, cuyos errores se llevan a los campos.
 */
export const PROFILE_LIMITS = {
  headline: 160,
  longText: 4000,
  location: 120,
  skills: 100,
  skillName: 80,
  experiences: 50,
  company: 120,
  role: 120,
  education: 20,
  educationText: 160,
  languages: 20,
  language: 60,
  languageLevel: 40,
  years: 60,
} as const;

/** Límite del `Integer` de Java: el año de la formación no tiene otro (sección 4). */
const JAVA_INT_MAX = 2_147_483_647;

const maxChars = (max: number) => `Máximo ${max} caracteres`;
const YEARS_MESSAGE = `Indica un número entero de 0 a ${PROFILE_LIMITS.years}`;

const optionalText = (max: number) => z.string().trim().max(max, maxChars(max));
const requiredText = (max: number, message: string) => z.string().trim().min(1, message).max(max, maxChars(max));

/** Vacío, o un entero de 0 a 60 (años de experiencia y años de una habilidad). */
const optionalYears = z
  .string()
  .trim()
  .refine((value) => value === '' || (/^\d+$/.test(value) && Number(value) <= PROFILE_LIMITS.years), YEARS_MESSAGE);

/** Vacío, o un entero (sin rango: la especificación no lo pone). */
const optionalYear = z
  .string()
  .trim()
  .refine(
    (value) => value === '' || (/^-?\d+$/.test(value) && Math.abs(Number(value)) <= JAVA_INT_MAX),
    'Indica un año (número entero)',
  );

const skillSchema = z.object({
  name: requiredText(PROFILE_LIMITS.skillName, 'Escribe el nombre de la habilidad'),
  level: z.enum(['', 'BASICO', 'INTERMEDIO', 'AVANZADO']),
  years: optionalYears,
});

/** Las reglas entre campos se comprueban aunque falle otro campo (`when`), para mostrar todos los errores a la vez. */
const always = () => true;

const experienceSchema = z
  .object({
    company: requiredText(PROFILE_LIMITS.company, 'Escribe la empresa'),
    role: requiredText(PROFILE_LIMITS.role, 'Escribe el puesto'),
    startDate: z.string().min(1, 'Indica la fecha de inicio'),
    // Opcional: con "Puesto actual" el campo está deshabilitado y React Hook Form no da su valor.
    endDate: z.string().optional(),
    current: z.boolean(),
    description: optionalText(PROFILE_LIMITS.longText),
  })
  .refine((experience) => experience.current || (experience.endDate ?? '') !== '', {
    path: ['endDate'],
    message: 'Indica la fecha de fin o marca "Puesto actual"',
    when: always,
  })
  .refine(
    // Fechas `YYYY-MM-DD` de <input type="date">: el orden de texto es el cronológico.
    (experience) =>
      experience.current ||
      !experience.endDate ||
      experience.startDate === '' ||
      experience.endDate >= experience.startDate,
    { path: ['endDate'], message: 'La fecha de fin no puede ser anterior a la de inicio', when: always },
  );

const educationSchema = z.object({
  title: requiredText(PROFILE_LIMITS.educationText, 'Escribe el título'),
  institution: optionalText(PROFILE_LIMITS.educationText),
  year: optionalYear,
});

const languageSchema = z.object({
  language: requiredText(PROFILE_LIMITS.language, 'Escribe el idioma'),
  level: requiredText(PROFILE_LIMITS.languageLevel, 'Escribe el nivel'),
});

/** Clave de unicidad de una habilidad, como `ProfileService.skillKey` (sin espacios y sin distinguir mayúsculas). */
export function skillKey(name: string): string {
  return name.trim().toLowerCase();
}

export const profileFormSchema = z.object({
  headline: optionalText(PROFILE_LIMITS.headline),
  summary: optionalText(PROFILE_LIMITS.longText),
  yearsExperience: optionalYears,
  location: optionalText(PROFILE_LIMITS.location),
  skills: z
    .array(skillSchema)
    .max(PROFILE_LIMITS.skills, `Como máximo ${PROFILE_LIMITS.skills} habilidades`)
    .superRefine((skills, ctx) => {
      const seen = new Set<string>();
      skills.forEach((skill, index) => {
        const key = skillKey(skill.name);
        if (key === '') return;
        if (seen.has(key)) {
          ctx.addIssue({
            code: 'custom',
            path: [index, 'name'],
            message: 'La habilidad está repetida (no se distinguen mayúsculas)',
          });
        }
        seen.add(key);
      });
    }),
  experiences: z
    .array(experienceSchema)
    .max(PROFILE_LIMITS.experiences, `Como máximo ${PROFILE_LIMITS.experiences} experiencias`),
  education: z.array(educationSchema).max(PROFILE_LIMITS.education, `Como máximo ${PROFILE_LIMITS.education} formaciones`),
  languages: z.array(languageSchema).max(PROFILE_LIMITS.languages, `Como máximo ${PROFILE_LIMITS.languages} idiomas`),
});

export type ProfileFormValues = z.input<typeof profileFormSchema>;
export type SkillFormValues = ProfileFormValues['skills'][number];
export type ExperienceFormValues = ProfileFormValues['experiences'][number];
export type EducationFormValues = ProfileFormValues['education'][number];
export type LanguageFormValues = ProfileFormValues['languages'][number];

export function emptySkill(): SkillFormValues {
  return { name: '', level: '', years: '' };
}

export function emptyExperience(): ExperienceFormValues {
  return { company: '', role: '', startDate: '', endDate: '', current: false, description: '' };
}

export function emptyEducation(): EducationFormValues {
  return { title: '', institution: '', year: '' };
}

export function emptyLanguage(): LanguageFormValues {
  return { language: '', level: '' };
}

/** Formulario vacío: el perfil hecho a mano. */
export function emptyProfileForm(): ProfileFormValues {
  return { headline: '', summary: '', yearsExperience: '', location: '', skills: [], experiences: [], education: [], languages: [] };
}

const fromNumber = (value: number | null): string => (value === null ? '' : String(value));

/** Borrador de la IA (`CvDraftResponse`) o perfil guardado (`ProfileResponse`) → valores del formulario. */
export function toProfileForm(profile: ProfileRequest): ProfileFormValues {
  return {
    headline: profile.headline ?? '',
    summary: profile.summary ?? '',
    yearsExperience: fromNumber(profile.yearsExperience),
    location: profile.location ?? '',
    skills: profile.skills.map((skill) => ({ name: skill.name, level: skill.level ?? '', years: fromNumber(skill.years) })),
    experiences: profile.experiences.map((experience) => ({
      company: experience.company,
      role: experience.role,
      startDate: experience.startDate,
      endDate: experience.endDate ?? '',
      current: experience.endDate === null,
      description: experience.description ?? '',
    })),
    education: profile.education.map((item) => ({
      title: item.title,
      institution: item.institution ?? '',
      year: fromNumber(item.year),
    })),
    languages: profile.languages.map((item) => ({ language: item.language, level: item.level })),
  };
}

/** Texto sin espacios a los lados; vacío → `null`. */
function toText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

function toInteger(value: string): number | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : Number(trimmed);
}

/** Valores del formulario (ya validados) → `ProfileRequest`. "Puesto actual" se envía como `endDate: null`. */
export function toProfileRequest(values: ProfileFormValues): ProfileRequest {
  return {
    headline: toText(values.headline),
    summary: toText(values.summary),
    yearsExperience: toInteger(values.yearsExperience),
    location: toText(values.location),
    skills: values.skills.map((skill) => ({
      name: skill.name.trim(),
      level: skill.level === '' ? null : (skill.level satisfies SkillLevel),
      years: toInteger(skill.years),
    })),
    experiences: values.experiences.map((experience) => ({
      company: experience.company.trim(),
      role: experience.role.trim(),
      startDate: experience.startDate,
      endDate: experience.current ? null : toText(experience.endDate ?? ''),
      description: toText(experience.description),
    })),
    education: values.education.map((item) => ({
      title: item.title.trim(),
      institution: toText(item.institution),
      year: toInteger(item.year),
    })),
    languages: values.languages.map((item) => ({ language: item.language.trim(), level: item.level.trim() })),
  };
}

const LIST_FIELDS: Record<string, readonly string[]> = {
  skills: ['name', 'level', 'years'],
  experiences: ['company', 'role', 'startDate', 'endDate', 'description'],
  education: ['title', 'institution', 'year'],
  languages: ['language', 'level'],
};
const TOP_FIELDS: readonly string[] = ['headline', 'summary', 'yearsExperience', 'location'];

/**
 * Ruta de un error del backend (`skills[2].name`) → ruta del formulario (`skills.2.name`), o `null` si no corresponde
 * a ningún campo (el formulario muestra entonces un aviso general).
 */
export function toFormPath(field: string): Path<ProfileFormValues> | null {
  if (TOP_FIELDS.includes(field)) return field as Path<ProfileFormValues>;
  const match = /^(\w+)\[(\d+)]\.(\w+)$/.exec(field);
  if (!match) return null;
  const [, list = '', index = '', name = ''] = match;
  if (!LIST_FIELDS[list]?.includes(name)) return null;
  return `${list}.${index}.${name}` as Path<ProfileFormValues>;
}
