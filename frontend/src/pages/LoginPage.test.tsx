import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { LoginPage } from './LoginPage';
import { AuthProvider } from '../auth/AuthContext';
import { tokenStore } from '../auth/tokenStore';
import { ApiError } from '../api/http';
import { login, me, register } from '../api/auth';

vi.mock('../api/auth', () => ({
  register: vi.fn(),
  login: vi.fn(),
  me: vi.fn(),
}));

const loginMock = vi.mocked(login);
const registerMock = vi.mocked(register);
const meMock = vi.mocked(me);

const USERNAME_RULE = 'Letters, digits and underscore only, max 50 chars';
const PASSWORD_RULE = 'At least 4 characters';

function tokenResponse() {
  return { accessToken: 'jwt-token', tokenType: 'Bearer', expiresInSeconds: 3600 };
}

type InitialEntry = string | { pathname: string; state?: unknown };

function renderLoginPage(initialEntry: InitialEntry = '/login') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/game" element={<p>Game probe</p>} />
          <Route path="/secret" element={<p>Secret probe</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>, name: string, pw: string) {
  if (name !== '') {
    await user.type(screen.getByLabelText('Username'), name);
  }
  if (pw !== '') {
    await user.type(screen.getByLabelText('Password'), pw);
  }
  await user.click(screen.getByRole('button', { name: 'Log in' }));
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it('blocks submit and shows the rule for an invalid username', async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText('Username'), 'bad name!');
    await user.type(screen.getByLabelText('Password'), 'password1');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText(USERNAME_RULE)).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('blocks submit and shows the rule for a too-short password', async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await fillAndSubmit(user, 'alice', 'abc');

    expect(await screen.findByText(PASSWORD_RULE)).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('clears a field error once the field changes', async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await fillAndSubmit(user, 'bad name!', 'password1');
    expect(await screen.findByText(USERNAME_RULE)).toBeInTheDocument();

    await user.type(screen.getByLabelText('Username'), '_');
    expect(screen.queryByText(USERNAME_RULE)).not.toBeInTheDocument();
  });

  it('on login success stores the session and navigates to /game', async () => {
    loginMock.mockResolvedValue(tokenResponse());
    const user = userEvent.setup();
    renderLoginPage();

    await fillAndSubmit(user, 'alice', 'password1');

    expect(await screen.findByText('Game probe')).toBeInTheDocument();
    expect(loginMock).toHaveBeenCalledWith({ username: 'alice', password: 'password1' });
    expect(tokenStore.load()?.username).toBe('alice');
    expect(tokenStore.load()?.accessToken).toBe('jwt-token');
  });

  it('on login success navigates back to the path in state.from', async () => {
    loginMock.mockResolvedValue(tokenResponse());
    const user = userEvent.setup();
    renderLoginPage({ pathname: '/login', state: { from: { pathname: '/secret' } } });

    await fillAndSubmit(user, 'alice', 'password1');

    expect(await screen.findByText('Secret probe')).toBeInTheDocument();
    expect(screen.queryByText('Game probe')).not.toBeInTheDocument();
  });

  it('shows a form-level error on 401 invalid-credentials and does not navigate', async () => {
    loginMock.mockRejectedValue(
      new ApiError(401, {
        type: 'urn:amiss:invalid-credentials',
        title: 'Unauthorized',
        status: 401,
      }),
    );
    const user = userEvent.setup();
    renderLoginPage();

    await fillAndSubmit(user, 'alice', 'password1');

    expect(await screen.findByText('Wrong username or password.')).toBeInTheDocument();
    expect(screen.queryByText('Game probe')).not.toBeInTheDocument();
    expect(tokenStore.load()).toBeNull();
  });

  it('shows a network failure as "Could not reach the server."', async () => {
    loginMock.mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderLoginPage();

    await fillAndSubmit(user, 'alice', 'password1');

    expect(await screen.findByText('Could not reach the server.')).toBeInTheDocument();
    expect(screen.queryByText('Game probe')).not.toBeInTheDocument();
  });

  it('register mode: 409 username-taken shows a username field error', async () => {
    registerMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:username-taken',
        title: 'Conflict',
        status: 409,
        detail: 'Username alice is already taken',
      }),
    );
    const user = userEvent.setup();
    renderLoginPage();

    await user.click(screen.getByRole('button', { name: 'Create one' }));
    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'password1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('That username is taken.')).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
    expect(screen.queryByText('Game probe')).not.toBeInTheDocument();
  });

  it('register mode: 400 invalid-registration shows the server detail as a form error', async () => {
    registerMock.mockRejectedValue(
      new ApiError(400, {
        type: 'urn:amiss:invalid-registration',
        title: 'Bad Request',
        status: 400,
        detail: 'Password must be at least 4 characters',
      }),
    );
    const user = userEvent.setup();
    renderLoginPage();

    await user.click(screen.getByRole('button', { name: 'Create one' }));
    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'password1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Password must be at least 4 characters')).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('register success registers, then logs in, then redirects to /game', async () => {
    registerMock.mockResolvedValue({ username: 'alice' });
    loginMock.mockResolvedValue(tokenResponse());
    const user = userEvent.setup();
    renderLoginPage();

    await user.click(screen.getByRole('button', { name: 'Create one' }));
    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'password1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Game probe')).toBeInTheDocument();
    expect(registerMock).toHaveBeenCalledWith({ username: 'alice', password: 'password1' });
    expect(loginMock).toHaveBeenCalledWith({ username: 'alice', password: 'password1' });
    expect(registerMock.mock.invocationCallOrder[0]).toBeLessThan(
      loginMock.mock.invocationCallOrder[0],
    );
    expect(tokenStore.load()?.username).toBe('alice');
  });

  it('redirects an already-authenticated user straight to /game', async () => {
    tokenStore.save({
      accessToken: 'jwt-token',
      username: 'alice',
      expiresAt: Date.now() + 3_600_000,
    });
    meMock.mockResolvedValue({ username: 'alice' });
    renderLoginPage();

    expect(await screen.findByText('Game probe')).toBeInTheDocument();
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument();
  });
});
