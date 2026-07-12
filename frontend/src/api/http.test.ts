import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch, onUnauthorized } from './http';
import { tokenStore } from '../auth/tokenStore';

function fakeResponse(status: number, body?: unknown): Response {
  const text = body === undefined ? '' : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: 'Status Text',
    json: () =>
      text === ''
        ? Promise.reject(new SyntaxError('Unexpected end of JSON input'))
        : Promise.resolve(JSON.parse(text)),
    text: () => Promise.resolve(text),
  } as unknown as Response;
}

function liveSession() {
  return {
    accessToken: 'live-token',
    username: 'alice',
    expiresAt: Date.now() + 3_600_000,
  };
}

function requestInitOfCall(fetchMock: ReturnType<typeof vi.fn>, callIndex = 0): RequestInit {
  return fetchMock.mock.calls[callIndex][1] as RequestInit;
}

describe('apiFetch', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('parses a 200 JSON response to the typed value', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, { username: 'alice' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await apiFetch<{ username: string }>('/auth/me');

    expect(result).toEqual({ username: 'alice' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/me');
  });

  it('throws ApiError carrying status/type/detail on a problem+json error', async () => {
    const problem = {
      type: 'urn:amiss:invalid-credentials',
      title: 'Unauthorized',
      status: 401,
      detail: 'Bad username or password',
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse(401, problem)));

    const err: unknown = await apiFetch('/auth/login', {
      method: 'POST',
      body: { username: 'alice', password: 'wrong' },
    }).then(
      () => {
        throw new Error('expected apiFetch to reject');
      },
      (e: unknown) => e,
    );

    expect(err).toBeInstanceOf(ApiError);
    const apiError = err as ApiError;
    expect(apiError.status).toBe(401);
    expect(apiError.problem.type).toBe('urn:amiss:invalid-credentials');
    expect(apiError.problem.detail).toBe('Bad username or password');
    expect(apiError.message).toBe('Bad username or password');
  });

  it('sends a Bearer header when the token store holds a live token', async () => {
    tokenStore.save(liveSession());
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, { username: 'alice' }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/auth/me');

    const headers = requestInitOfCall(fetchMock).headers as Record<string, string>;
    expect(headers['Authorization']).toBe('Bearer live-token');
  });

  it('sends no Bearer header when there is no stored token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, []));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/board');

    const headers = requestInitOfCall(fetchMock).headers as Record<string, string>;
    expect(headers['Authorization']).toBeUndefined();
  });

  it('fires the onUnauthorized handler on a 401 for a token-carrying request', async () => {
    tokenStore.save(liveSession());
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(fakeResponse(401, { type: 'urn:amiss:unauthenticated' })),
    );
    const handler = vi.fn();
    onUnauthorized(handler);

    await expect(apiFetch('/auth/me')).rejects.toBeInstanceOf(ApiError);

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does not fire the onUnauthorized handler on a 401 without a token', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(fakeResponse(401, { type: 'urn:amiss:invalid-credentials' })),
    );
    const handler = vi.fn();
    onUnauthorized(handler);

    await expect(
      apiFetch('/auth/login', { method: 'POST', body: { username: 'alice', password: 'wrong' } }),
    ).rejects.toBeInstanceOf(ApiError);

    expect(handler).not.toHaveBeenCalled();
  });
});
