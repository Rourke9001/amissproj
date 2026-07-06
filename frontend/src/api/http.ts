import { tokenStore } from '../auth/tokenStore';
import type { ProblemDetail } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

let unauthorizedHandler: (() => void) | null = null;

/**
 * Registers the callback fired when an authenticated request comes back 401.
 * The auth layer registers its logout here; a 401 on a request that carried
 * no token (e.g. a failed login) does not fire it.
 */
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler;
}

export async function apiFetch<T>(
  path: string,
  init?: { method?: string; body?: unknown },
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const hasBody = init?.body !== undefined;
  if (hasBody) {
    headers['Content-Type'] = 'application/json';
  }

  const session = tokenStore.load();
  if (session !== null) {
    headers['Authorization'] = `Bearer ${session.accessToken}`;
  }

  const res = await fetch(`/api${path}`, {
    method: init?.method ?? 'GET',
    headers,
    body: hasBody ? JSON.stringify(init.body) : undefined,
  });

  if (!res.ok) {
    let problem: ProblemDetail = { status: res.status, title: res.statusText };
    try {
      const parsed: unknown = await res.json();
      if (typeof parsed === 'object' && parsed !== null) {
        problem = parsed as ProblemDetail;
      }
    } catch {
      // Body was empty or not JSON; keep the statusText fallback.
    }
    if (res.status === 401 && session !== null) {
      unauthorizedHandler?.();
    }
    throw new ApiError(res.status, problem);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  if (text === '') {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}
