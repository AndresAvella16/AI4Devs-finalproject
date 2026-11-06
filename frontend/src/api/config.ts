/**
 * Base de la API. Por defecto es la ruta relativa `/api/v1`: en desarrollo la reenvía el proxy de Vite al backend
 * y en producción, nginx. Así no hay CORS y el mismo build vale para cualquier entorno.
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/+$/, '');
