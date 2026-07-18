import { apiFetch } from './http';
import type { RelaxResponse } from './types';

export function relax(saveId: number): Promise<RelaxResponse> {
  return apiFetch<RelaxResponse>(`/saves/${saveId}/relax`, { method: 'POST' });
}
