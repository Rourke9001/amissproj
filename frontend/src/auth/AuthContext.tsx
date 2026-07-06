import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { login as apiLogin, me } from '../api/auth';
import { ApiError, onUnauthorized } from '../api/http';
import { tokenStore } from './tokenStore';

interface AuthContextValue {
  username: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(
    () => tokenStore.load()?.username ?? null,
  );

  const logout = useCallback(() => {
    tokenStore.clear();
    setUsername(null);
  }, []);

  const login = useCallback(async (name: string, password: string) => {
    const res = await apiLogin({ username: name, password });
    tokenStore.save({
      accessToken: res.accessToken,
      username: name,
      expiresAt: Date.now() + res.expiresInSeconds * 1000,
    });
    setUsername(name);
  }, []);

  useEffect(() => {
    // There is no refresh endpoint: a 401 on any authenticated request means
    // the session is unrecoverable, so the HTTP layer hands it to logout.
    onUnauthorized(logout);
    if (tokenStore.load() !== null) {
      me().catch((err: unknown) => {
        if (err instanceof ApiError) {
          logout();
        }
      });
    }
  }, [logout]);

  const value = useMemo(() => ({ username, login, logout }), [username, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (ctx === null) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
