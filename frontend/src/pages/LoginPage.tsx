import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import type { Location } from 'react-router';
import { register } from '../api/auth';
import { ApiError } from '../api/http';
import { useAuth } from '../auth/AuthContext';

// Mirrors the server-side rules in amiss-core's Validation.java so users get
// instant feedback; the server re-validates and stays the source of truth.
const USERNAME_PATTERN = /^[A-Za-z0-9_]{1,50}$/;
const MIN_PASSWORD_LENGTH = 4;
const USERNAME_RULE = 'Letters, digits and underscore only, max 50 chars';
const PASSWORD_RULE = 'At least 4 characters';

type Mode = 'login' | 'register';

interface FieldErrors {
  username?: string;
  password?: string;
}

export function LoginPage() {
  const { username: sessionUsername, login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Where to go once authenticated: back to the page RequireAuth bounced the
  // user from, or the game by default.
  const from = (location.state as { from?: Location })?.from?.pathname ?? '/game';

  // Also covers the moment right after a successful login: the auth state
  // update can re-render this page before the navigate() transition commits,
  // and this redirect must then agree with it on the target.
  if (sessionUsername !== null) {
    return <Navigate to={from} replace />;
  }

  const switchMode = (next: Mode) => {
    setMode(next);
    setFieldErrors({});
    setFormError(null);
  };

  const handleUsernameChange = (value: string) => {
    setUsername(value);
    setFieldErrors((prev) =>
      prev.username !== undefined ? { ...prev, username: undefined } : prev,
    );
  };

  const handlePasswordChange = (value: string) => {
    setPassword(value);
    setFieldErrors((prev) =>
      prev.password !== undefined ? { ...prev, password: undefined } : prev,
    );
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);

    const errors: FieldErrors = {};
    if (!USERNAME_PATTERN.test(username)) {
      errors.username = USERNAME_RULE;
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      errors.password = PASSWORD_RULE;
    }
    setFieldErrors(errors);
    if (errors.username !== undefined || errors.password !== undefined) {
      return;
    }

    setSubmitting(true);
    try {
      if (mode === 'register') {
        await register({ username, password });
      }
      await login(username, password);
      navigate(from, { replace: true });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.problem.type === 'urn:amiss:invalid-credentials') {
          setFormError('Wrong username or password.');
        } else if (err.problem.type === 'urn:amiss:username-taken') {
          setFieldErrors((prev) => ({ ...prev, username: 'That username is taken.' }));
        } else {
          setFormError(err.problem.detail ?? err.problem.title ?? err.message);
        }
      } else {
        setFormError('Could not reach the server.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="auth-card">
      <h1>{mode === 'login' ? 'Log in' : 'Create account'}</h1>
      <form onSubmit={handleSubmit} noValidate>
        {formError !== null && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
        <div className="field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(e) => handleUsernameChange(e.target.value)}
            aria-invalid={fieldErrors.username !== undefined}
            aria-describedby={fieldErrors.username !== undefined ? 'username-error' : undefined}
          />
          {fieldErrors.username !== undefined && (
            <p className="field-error" id="username-error" role="alert">
              {fieldErrors.username}
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={password}
            onChange={(e) => handlePasswordChange(e.target.value)}
            aria-invalid={fieldErrors.password !== undefined}
            aria-describedby={fieldErrors.password !== undefined ? 'password-error' : undefined}
          />
          {fieldErrors.password !== undefined && (
            <p className="field-error" id="password-error" role="alert">
              {fieldErrors.password}
            </p>
          )}
        </div>
        <button type="submit" disabled={submitting}>
          {mode === 'login' ? 'Log in' : 'Create account'}
        </button>
      </form>
      <p className="auth-switch">
        {mode === 'login' ? (
          <>
            No account?{' '}
            <button
              type="button"
              className="link-button"
              disabled={submitting}
              onClick={() => switchMode('register')}
            >
              Create one
            </button>
          </>
        ) : (
          <>
            Already have an account?{' '}
            <button
              type="button"
              className="link-button"
              disabled={submitting}
              onClick={() => switchMode('login')}
            >
              Log in here
            </button>
          </>
        )}
      </p>
    </section>
  );
}
