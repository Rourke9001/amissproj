export interface StoredSession {
  accessToken: string;
  username: string;
  /** Epoch milliseconds. */
  expiresAt: number;
}

const STORAGE_KEY = 'amiss.auth.v1';
const CLOCK_SKEW_MS = 30_000;

// The API issues no refresh tokens, so an expired (or nearly expired, within
// the clock-skew margin) token cannot be renewed: it is treated as absent and
// removed, forcing a fresh login.
export const tokenStore = {
  save(session: StoredSession): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  },

  load(): StoredSession | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      return null;
    }
    try {
      const session = JSON.parse(raw) as Partial<StoredSession>;
      if (
        typeof session.accessToken === 'string' &&
        typeof session.username === 'string' &&
        typeof session.expiresAt === 'number' &&
        session.expiresAt - CLOCK_SKEW_MS > Date.now()
      ) {
        return session as StoredSession;
      }
    } catch {
      // Corrupt JSON: treat as absent.
    }
    localStorage.removeItem(STORAGE_KEY);
    return null;
  },

  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
  },
};
