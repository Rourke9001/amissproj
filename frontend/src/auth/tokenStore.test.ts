import { beforeEach, describe, expect, it } from 'vitest';
import { tokenStore } from './tokenStore';
import type { StoredSession } from './tokenStore';

const STORAGE_KEY = 'amiss.auth.v1';

describe('tokenStore', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('round-trips a live session', () => {
    const session: StoredSession = {
      accessToken: 'token-123',
      username: 'alice',
      expiresAt: Date.now() + 3_600_000,
    };
    tokenStore.save(session);
    expect(tokenStore.load()).toEqual(session);
  });

  it('returns null for an expired session and removes the entry', () => {
    tokenStore.save({
      accessToken: 'token-123',
      username: 'alice',
      expiresAt: Date.now() - 1_000,
    });
    expect(tokenStore.load()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('returns null for corrupt JSON and removes the entry', () => {
    localStorage.setItem(STORAGE_KEY, '{not valid json');
    expect(tokenStore.load()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('treats a session expiring within the 30s skew window as absent', () => {
    tokenStore.save({
      accessToken: 'token-123',
      username: 'alice',
      expiresAt: Date.now() + 10_000,
    });
    expect(tokenStore.load()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('clear removes the stored session', () => {
    tokenStore.save({
      accessToken: 'token-123',
      username: 'alice',
      expiresAt: Date.now() + 3_600_000,
    });
    tokenStore.clear();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
