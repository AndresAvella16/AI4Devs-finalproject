import { useMutation } from '@tanstack/react-query';
import { login, register, type AuthResponse, type LoginRequest, type RegisterRequest } from '../../api/auth';
import type { ApiError } from '../../api/ApiError';
import { useAuth } from './useAuth';

/** `POST /auth/login`; si va bien, inicia la sesión. */
export function useLogin() {
  const { signIn } = useAuth();
  return useMutation<AuthResponse, ApiError | Error, LoginRequest>({ mutationFn: login, onSuccess: signIn });
}

/** `POST /auth/register`; si va bien, inicia la sesión (el backend devuelve el token en el 201). */
export function useRegister() {
  const { signIn } = useAuth();
  return useMutation<AuthResponse, ApiError | Error, RegisterRequest>({ mutationFn: register, onSuccess: signIn });
}
