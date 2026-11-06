import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section className="text-center">
      <h1 className="text-2xl font-bold text-slate-900">Página no encontrada</h1>
      <p className="mt-2 text-slate-600">La dirección que has abierto no existe.</p>
      <Link to="/" className="mt-4 inline-block font-medium text-indigo-700 underline-offset-2 hover:underline">
        Volver al inicio
      </Link>
    </section>
  );
}
