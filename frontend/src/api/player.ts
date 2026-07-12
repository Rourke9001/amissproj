import { apiFetch } from './http';
import type { EndWeekResponse, MoveResponse, SaveStateDto } from './types';

export function getSaveState(saveId: number): Promise<SaveStateDto> {
  return apiFetch<SaveStateDto>(`/saves/${saveId}`);
}

export function move(saveId: number, target: string): Promise<MoveResponse> {
  return apiFetch<MoveResponse>(`/saves/${saveId}/move`, { method: 'POST', body: { target } });
}

export function endWeek(saveId: number): Promise<EndWeekResponse> {
  return apiFetch<EndWeekResponse>(`/saves/${saveId}/end-week`, { method: 'POST' });
}
