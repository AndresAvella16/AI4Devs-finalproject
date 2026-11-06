import { useAuth } from '../features/auth/useAuth';

/** Inicio provisional. Lo sustituirá el dashboard de ofertas (JP-FE-03). */
export function HomePage() {
  const { session } = useAuth();

  return (
    <section>
      <h1 className="text-2xl font-bold text-slate-900" data-testid="home-greeting">
        Hola, {session?.user.fullName}
      </h1>
      <p className="mt-2 text-slate-600">Aquí verás tus ofertas importadas y su encaje con tu perfil.</p>
    </section>
  );
}
