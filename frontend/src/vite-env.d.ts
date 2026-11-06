/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base de la API. Vacía = `/api/v1` (ruta relativa, servida por el proxy de Vite o por nginx). */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
