import { apiFetch } from './client';

/** `RegisterRequest` (sección 4). */
export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

/** `LoginRequest` (sección 4). */
export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
}

/** `AuthResponse` (sección 4). `expiresIn` en segundos. */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: AuthUser;
}

export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/auth/login', { method: 'POST', body: request, auth: false });
}

export function register(request: RegisterRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/auth/register', { method: 'POST', body: request, auth: false });
}
