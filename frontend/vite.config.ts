/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// En desarrollo el navegador habla solo con Vite (:5173) y Vite reenvía /api al backend:
// misma ruta relativa (/api/v1) que con nginx en producción y sin CORS.
const backendUrl = 'http://localhost:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backendUrl, changeOrigin: true },
    },
  },
  build: {
    rolldownOptions: {
      output: {
        // React, React DOM y React Router van en su propio chunk (JP-FE-02): el inicial pasaba de 500 kB. Se descargan
        // igual en la primera carga, pero cambian poco y el navegador los conserva en caché entre despliegues.
        codeSplitting: {
          groups: [{ name: 'vendor-react', test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/ }],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
});
