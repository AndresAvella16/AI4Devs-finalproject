import { Link } from 'react-router';

/**
 * `/perfil` provisional: a esta ruta se llega al calcular el encaje sin perfil (409 `profile-required`).
 * JP-FE-02 la sustituye por el onboarding (subida del CV y formulario del perfil).
 */
export function ProfilePlaceholderPage() {
  return (
    <section data-testid="profile-placeholder" className="flex flex-col items-start gap-3">
      <h1 className="text-2xl font-bold text-slate-900">Crea tu perfil</h1>
      <p className="text-slate-600">
        La creación del perfil a partir de tu CV llegará en la próxima versión. Mientras tanto, puedes probar el
        análisis con el usuario de demostración (demo@jobpilot.dev).
      </p>
      <Link to="/" className="text-sm font-medium text-indigo-700 underline-offset-2 hover:underline">
        Volver a tus ofertas
      </Link>
    </section>
  );
}
