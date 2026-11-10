import { Link, NavLink, Outlet } from 'react-router';
import { Button } from '../components/Button';
import { useAuth } from '../features/auth/useAuth';

const NAV_LINKS = [
  { to: '/', label: 'Ofertas', testId: 'nav-offers', end: true },
  { to: '/tablero', label: 'Tablero', testId: 'nav-board', end: false },
] as const;

export function Layout() {
  const { session, signOut } = useAuth();

  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:rounded focus:bg-white focus:px-3 focus:py-2"
      >
        Saltar al contenido
      </a>
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link
            to="/"
            className="rounded text-lg font-bold text-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          >
            JobPilot
          </Link>
          {session && (
            <nav aria-label="Principal" className="mr-auto flex items-center gap-1">
              {NAV_LINKS.map(({ to, label, testId, end }) => (
                <NavLink
                  key={to}
                  to={to}
                  end={end}
                  data-testid={testId}
                  className={({ isActive }) =>
                    `rounded px-3 py-1.5 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 ${
                      isActive ? 'bg-indigo-50 text-indigo-800' : 'text-slate-700 hover:bg-slate-100'
                    }`
                  }
                >
                  {label}
                </NavLink>
              ))}
            </nav>
          )}
          {session && (
            <div className="flex items-center gap-3">
              <span className="text-sm text-slate-700" data-testid="current-user-name">
                {session.user.fullName}
              </span>
              <Button variant="secondary" data-testid="logout-button" onClick={() => signOut('logout')}>
                Cerrar sesión
              </Button>
            </div>
          )}
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
