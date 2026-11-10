import { describe, expect, it } from 'vitest';
import type { ProfileRequest } from '../../api/profile';
import { exampleCvDraft } from '../../test/handlers';
import {
  emptyExperience,
  emptyProfileForm,
  emptySkill,
  profileFormSchema,
  toFormPath,
  toProfileForm,
  toProfileRequest,
  type ProfileFormValues,
} from './schemas';

/** Rutas (`skills.1.name`) y mensajes de los errores de Zod. */
function issues(values: ProfileFormValues): Record<string, string> {
  const result = profileFormSchema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(result.error.issues.map((issue) => [issue.path.join('.'), issue.message]));
}

function withValues(overrides: Partial<ProfileFormValues>): ProfileFormValues {
  return { ...toProfileForm(exampleCvDraft()), ...overrides };
}

describe('schemas del perfil — formulario ↔ ProfileRequest (sección 4)', () => {
  it('el borrador de la IA pasa al formulario y vuelve al mismo ProfileRequest', () => {
    const draft = exampleCvDraft();
    const request: ProfileRequest = {
      headline: draft.headline,
      summary: draft.summary,
      yearsExperience: draft.yearsExperience,
      location: draft.location,
      skills: draft.skills,
      experiences: draft.experiences,
      education: draft.education,
      languages: draft.languages,
    };
    const form = toProfileForm(request);

    expect(form.yearsExperience).toBe('2');
    expect(form.skills[0]).toEqual({ name: 'Java', level: 'AVANZADO', years: '2' });
    expect(form.experiences[0]).toMatchObject({ startDate: '2025-03-01', endDate: '', current: true });
    expect(issues(form)).toEqual({});
    expect(toProfileRequest(form)).toEqual(request);
  });

  it('los textos vacíos se envían como null, se quitan los espacios y "puesto actual" es endDate null', () => {
    const form = withValues({
      headline: '  ',
      summary: '',
      yearsExperience: '',
      location: ' Valencia ',
      skills: [{ name: ' Java ', level: '', years: '' }],
      experiences: [
        { company: 'A', role: 'B', startDate: '2024-01-01', endDate: '2024-06-30', current: true, description: ' ' },
      ],
      education: [{ title: 'DAW', institution: '', year: '' }],
      languages: [{ language: 'Inglés', level: 'B2' }],
    });

    expect(issues(form)).toEqual({});
    expect(toProfileRequest(form)).toEqual({
      headline: null,
      summary: null,
      yearsExperience: null,
      location: 'Valencia',
      skills: [{ name: 'Java', level: null, years: null }],
      experiences: [{ company: 'A', role: 'B', startDate: '2024-01-01', endDate: null, description: null }],
      education: [{ title: 'DAW', institution: null, year: null }],
      languages: [{ language: 'Inglés', level: 'B2' }],
    });
  });

  it('un perfil vacío (hecho a mano) es válido', () => {
    expect(issues(emptyProfileForm())).toEqual({});
    expect(toProfileRequest(emptyProfileForm())).toEqual({
      headline: null,
      summary: null,
      yearsExperience: null,
      location: null,
      skills: [],
      experiences: [],
      education: [],
      languages: [],
    });
  });

  it('respeta las longitudes máximas de ProfileRequest', () => {
    expect(
      issues(withValues({ headline: 'a'.repeat(161), summary: 'a'.repeat(4001), location: 'a'.repeat(121) })),
    ).toEqual({
      headline: 'Máximo 160 caracteres',
      summary: 'Máximo 4000 caracteres',
      location: 'Máximo 120 caracteres',
    });
    expect(issues(withValues({ headline: 'a'.repeat(160), summary: 'a'.repeat(4000) }))).toEqual({});
  });

  it.each(['-1', '61', '2.5', 'dos'])('años de experiencia "%s" no es válido (entero de 0 a 60)', (years) => {
    expect(issues(withValues({ yearsExperience: years }))).toEqual({ yearsExperience: 'Indica un número entero de 0 a 60' });
  });

  it('una habilidad necesita nombre y sus años van de 0 a 60', () => {
    expect(
      issues(withValues({ skills: [{ name: ' ', level: '', years: '' }, { name: 'Git', level: '', years: '61' }] })),
    ).toEqual({ 'skills.0.name': 'Escribe el nombre de la habilidad', 'skills.1.years': 'Indica un número entero de 0 a 60' });
  });

  it('las habilidades no se repiten sin distinguir mayúsculas ni espacios', () => {
    const skills = [
      { name: 'Java', level: '', years: '' },
      { name: 'Spring Boot', level: '', years: '' },
      { name: ' java ', level: '', years: '' },
    ] satisfies ProfileFormValues['skills'];

    expect(issues(withValues({ skills }))).toEqual({
      'skills.2.name': 'La habilidad está repetida (no se distinguen mayúsculas)',
    });
  });

  it('una experiencia necesita empresa, puesto, inicio y fin (o "puesto actual")', () => {
    expect(issues(withValues({ experiences: [emptyExperience()] }))).toEqual({
      'experiences.0.company': 'Escribe la empresa',
      'experiences.0.role': 'Escribe el puesto',
      'experiences.0.startDate': 'Indica la fecha de inicio',
      'experiences.0.endDate': 'Indica la fecha de fin o marca "Puesto actual"',
    });
  });

  it('la fecha de fin no puede ser anterior a la de inicio', () => {
    const experience = { ...emptyExperience(), company: 'A', role: 'B', startDate: '2024-05-01' };

    expect(issues(withValues({ experiences: [{ ...experience, endDate: '2024-04-30' }] }))).toEqual({
      'experiences.0.endDate': 'La fecha de fin no puede ser anterior a la de inicio',
    });
    expect(issues(withValues({ experiences: [{ ...experience, endDate: '2024-05-01' }] }))).toEqual({});
  });

  it('formación con título y año entero (sin rango), e idioma con nivel', () => {
    expect(
      issues(
        withValues({
          education: [{ title: '', institution: '', year: '2024.5' }],
          languages: [{ language: 'Inglés', level: '' }],
        }),
      ),
    ).toEqual({
      'education.0.title': 'Escribe el título',
      'education.0.year': 'Indica un año (número entero)',
      'languages.0.level': 'Escribe el nivel',
    });
    expect(issues(withValues({ education: [{ title: 'DAW', institution: '', year: '1975' }] }))).toEqual({});
  });

  it('respeta el número máximo de elementos de cada lista', () => {
    const skills = Array.from({ length: 101 }, (_, i) => ({ ...emptySkill(), name: `Skill ${i}` }));
    const languages = Array.from({ length: 21 }, (_, i) => ({ language: `Idioma ${i}`, level: 'B1' }));

    expect(issues(withValues({ skills, languages }))).toEqual({
      skills: 'Como máximo 100 habilidades',
      languages: 'Como máximo 20 idiomas',
    });
  });

  it('toFormPath traduce las rutas de los errores del backend a las del formulario', () => {
    expect(toFormPath('skills[2].name')).toBe('skills.2.name');
    expect(toFormPath('experiences[0].endDate')).toBe('experiences.0.endDate');
    expect(toFormPath('headline')).toBe('headline');
    expect(toFormPath('languages[1].level')).toBe('languages.1.level');
    expect(toFormPath('file')).toBeNull();
    expect(toFormPath('skills[0].unknown')).toBeNull();
  });
});
