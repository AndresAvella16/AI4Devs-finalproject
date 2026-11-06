import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { FormField } from '../../components/FormField';
import { AuthCard } from './AuthCard';
import { loginSchema, type LoginForm } from './schemas';
import { applyServerErrors, toGeneralError, type GeneralError } from './serverErrors';
import { useAuth } from './useAuth';
import { useLogin } from './useAuthMutations';

export function LoginPage() {
  const { signOutReason } = useAuth();
  const loginMutation = useLogin();
  const [generalError, setGeneralError] = useState<GeneralError | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } });

  const onSubmit = handleSubmit((values) => {
    setGeneralError(null);
    loginMutation.mutate(values, {
      onError: (error) => {
        if (!applyServerErrors(error, setError, ['email', 'password'])) setGeneralError(toGeneralError(error));
      },
    });
  });

  return (
    <AuthCard
      title="Iniciar sesión"
      footer={
        <>
          ¿No tienes cuenta?{' '}
          <Link to="/registro" className="font-medium text-indigo-700 underline-offset-2 hover:underline">
            Crea una cuenta
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4" aria-label="Iniciar sesión">
        {signOutReason === 'expired' && !generalError && (
          <Alert tone="info" title="Tu sesión ha caducado" testId="session-expired">
            Vuelve a iniciar sesión para continuar.
          </Alert>
        )}
        {generalError && (
          <Alert title={generalError.title} testId="login-error">
            {generalError.detail}
          </Alert>
        )}
        <FormField
          id="login-email"
          testId="login-email"
          label="Email"
          type="email"
          autoComplete="email"
          registration={register('email')}
          error={errors.email?.message}
        />
        <FormField
          id="login-password"
          testId="login-password"
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          registration={register('password')}
          error={errors.password?.message}
        />
        <Button type="submit" data-testid="login-submit" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? 'Entrando…' : 'Iniciar sesión'}
        </Button>
      </form>
    </AuthCard>
  );
}
