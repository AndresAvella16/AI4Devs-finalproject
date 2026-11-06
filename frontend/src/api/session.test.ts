import { describe, expect, it } from 'vitest';
import { authResponse, demoUser } from '../test/handlers';
import { clearSession, loadSession, saveSession, SESSION_STORAGE_KEY } from './session';

const NOW = Date.UTC(2026, 10, 6, 10, 0, 0);

describe('session', () => {
  it('guarda el token, el usuario y la caducidad calculada con expiresIn', () => {
    const saved = saveSession(authResponse(), NOW);

    expect(saved).toEqual({ accessToken: 'test.jwt.token', expiresAt: NOW + 3600 * 1000, user: demoUser });
    expect(loadSession(NOW + 1000)).toEqual(saved);
  });

  it('trata un token caducado como sesión cerrada y lo borra', () => {
    saveSession(authResponse(), NOW);

    expect(loadSession(NOW + 3600 * 1000)).toBeNull();
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it.each([
    ['JSON roto', '{no es json'],
    ['forma incorrecta', JSON.stringify({ accessToken: 42 })],
  ])('ignora y borra una sesión guardada no válida (%s)', (_caso, raw) => {
    localStorage.setItem(SESSION_STORAGE_KEY, raw);

    expect(loadSession(NOW)).toBeNull();
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('clearSession borra la sesión', () => {
    saveSession(authResponse(), NOW);
    clearSession();

    expect(loadSession(NOW)).toBeNull();
  });
});
