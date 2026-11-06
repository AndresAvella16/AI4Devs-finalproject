import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { Alert } from '../../components/Alert';
import { Button } from '../../components/Button';
import { FormField } from '../../components/FormField';
import { AuthCard } from './AuthCard';
import { registerSchema, type RegisterForm } from './schemas';
import { applyServerErrors, toGeneralError, type GeneralError } from './serverErrors';
import { useRegister } from './useAuthMutations';

export function RegisterPage() {
  const registerMutation = useRegister();
  const [generalError, setGeneralError] = useState<GeneralError | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '' },
  });

  const onSubmit = handleSubmit((values) => {
    setGeneralError(null);
    registerMutation.mutate(values, {
      onError: (error) => {
        const mapped = applyServerErrors(error, setError, ['fullName', 'email', 'password'], {
          'email-already-registered': 'email',
        });
        if (!mapped) setGeneralError(toGeneralError(error));
      },
    });
  });

  return (
    <AuthCard
      title="Crear cuenta"
      footer={
        <>
          ¿Ya tienes cuenta?{' '}
          <Link to="/login" className="font-medium text-indigo-700 underline-offset-2 hover:underline">
            Inicia sesión
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4" aria-label="Crear cuenta">
        {generalError && (
          <Alert title={generalError.title} testId="register-error">
            {generalError.detail}
          </Alert>
        )}
        <FormField
          id="register-fullname"
          testId="register-fullname"
          label="Nombre completo"
          autoComplete="name"
          registration={register('fullName')}
          error={errors.fullName?.message}
        />
        <FormField
          id="register-email"
          testId="register-email"
          label="Email"
          type="email"
          autoComplete="email"
          registration={register('email')}
          error={errors.email?.message}
        />
        <FormField
          id="register-password"
          testId="register-password"
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          hint="Mínimo 8 caracteres, con al menos una letra y un número."
          registration={register('password')}
          error={errors.password?.message}
        />
        <Button type="submit" data-testid="register-submit" disabled={registerMutation.isPending}>
          {registerMutation.isPending ? 'Creando cuenta…' : 'Crear cuenta'}
        </Button>
      </form>
    </AuthCard>
  );
}
