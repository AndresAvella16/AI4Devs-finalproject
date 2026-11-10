import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ApiError } from '../../api/ApiError';
import {
  getProfile,
  saveProfile,
  uploadCv,
  type CvDraftResponse,
  type ProfileRequest,
  type SavedProfile,
} from '../../api/profile';

export const profileKeys = {
  all: ['profile'] as const,
};

/** `GET /profile`: el perfil, o `null` si el usuario aún no lo ha creado (404). */
export function useProfile() {
  return useQuery({ queryKey: profileKeys.all, queryFn: ({ signal }) => getProfile(signal) });
}

/** `POST /profile/cv`: el borrador de la IA. No toca la caché: el perfil no cambia hasta el `PUT`. */
export function useUploadCv() {
  return useMutation<CvDraftResponse, ApiError | Error, File>({ mutationFn: uploadCv });
}

/** `PUT /profile`. Deja el perfil guardado en la caché (el dashboard deja de pedir que se cree). */
export function useSaveProfile() {
  const queryClient = useQueryClient();
  return useMutation<SavedProfile, ApiError | Error, ProfileRequest>({
    mutationFn: saveProfile,
    onSuccess: ({ profile }) => queryClient.setQueryData(profileKeys.all, profile),
  });
}
