import { ApiError } from './ApiError';
import { apiFetch, apiRequest } from './client';

export type SkillLevel = 'BASICO' | 'INTERMEDIO' | 'AVANZADO';

export const SKILL_LEVELS: readonly SkillLevel[] = ['BASICO', 'INTERMEDIO', 'AVANZADO'];

/** `Skill` (sección 4). El nombre es único en el perfil sin distinguir mayúsculas. */
export interface Skill {
  name: string;
  level: SkillLevel | null;
  years: number | null;
}

/** `Experience` (sección 4). Las fechas son `YYYY-MM-DD`; `endDate` `null` = puesto actual. */
export interface Experience {
  company: string;
  role: string;
  startDate: string;
  endDate: string | null;
  description: string | null;
}

export interface LanguageItem {
  language: string;
  level: string;
}

export interface EducationItem {
  title: string;
  institution: string | null;
  year: number | null;
}

/** `ProfileRequest` (sección 4): el perfil completo que se guarda con `PUT /profile` (reemplaza el anterior). */
export interface ProfileRequest {
  headline: string | null;
  summary: string | null;
  yearsExperience: number | null;
  location: string | null;
  languages: LanguageItem[];
  education: EducationItem[];
  skills: Skill[];
  /** El orden del array es el orden en que se muestran. */
  experiences: Experience[];
}

/** `ProfileResponse` (sección 4). `cvFileName` es `null` si el perfil se ha hecho a mano. */
export interface ProfileResponse extends ProfileRequest {
  id: string;
  cvFileName: string | null;
  updatedAt: string;
}

/** `CvDraftResponse` (sección 4): el borrador que ha extraído la IA, sin guardar. */
export interface CvDraftResponse extends ProfileRequest {
  cvFileName: string;
}

export interface SavedProfile {
  profile: ProfileResponse;
  /** `true` si el `PUT` ha creado el perfil (201); `false` si lo ha reemplazado (200). */
  created: boolean;
}

/** `GET /profile`. Devuelve `null` si el usuario aún no tiene perfil (404). */
export async function getProfile(signal?: AbortSignal): Promise<ProfileResponse | null> {
  try {
    return await apiFetch<ProfileResponse>('/profile', { signal });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

/**
 * `POST /profile/cv` (multipart, campo `file`): devuelve el borrador sin guardarlo. 400 / 415 `invalid-cv-file` /
 * 422 `cv-without-text` no llegan a la IA; 429 / 502 / 503 / 504 son errores de la IA.
 */
export function uploadCv(file: File): Promise<CvDraftResponse> {
  const body = new FormData();
  body.append('file', file);
  return apiFetch<CvDraftResponse>('/profile/cv', { method: 'POST', body });
}

/** `PUT /profile`: 201 la primera vez, 200 después. */
export async function saveProfile(request: ProfileRequest): Promise<SavedProfile> {
  const { status, data } = await apiRequest<ProfileResponse>('/profile', { method: 'PUT', body: request });
  return { profile: data, created: status === 201 };
}
