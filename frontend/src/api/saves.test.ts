import { describe, expect, it, vi } from 'vitest';
import { createSave, deleteSave, listSaves } from './saves';

function fakeResponse(status: number, body: unknown): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api/saves', () => {
  it('listSaves calls GET /api/saves', async () => {
    const saves = [
      {
        id: 1,
        label: 'Save 1',
        round: 1,
        cash: 100,
        won: false,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ];
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, saves));
    vi.stubGlobal('fetch', fetchMock);

    await expect(listSaves()).resolves.toEqual(saves);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/saves');
    // apiFetch defaults an omitted init.method to 'GET' (see http.ts: `init?.method ?? 'GET'`),
    // so the value actually reaching fetch() is always the concrete string, never undefined.
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'GET' });

    vi.unstubAllGlobals();
  });

  it('createSave POSTs the request body to /api/saves', async () => {
    const created = {
      id: 2,
      label: 'New',
      round: 1,
      cash: 100,
      won: false,
      updatedAt: '2026-01-01T00:00:00Z',
    };
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(201, created));
    vi.stubGlobal('fetch', fetchMock);

    const request = { label: 'New', random: true };
    await expect(createSave(request)).resolves.toEqual(created);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/saves');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual(request);

    vi.unstubAllGlobals();
  });

  it('deleteSave DELETEs /api/saves/{id} and resolves undefined on 204', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(204, undefined));
    vi.stubGlobal('fetch', fetchMock);

    await expect(deleteSave(7)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][0]).toBe('/api/saves/7');
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('DELETE');

    vi.unstubAllGlobals();
  });
});
