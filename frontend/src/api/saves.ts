import { apiFetch } from './http';
import type { CreateSaveRequest, SaveSummaryDto } from './types';

export function listSaves(): Promise<SaveSummaryDto[]> {
  return apiFetch<SaveSummaryDto[]>('/saves');
}

export function createSave(request: CreateSaveRequest): Promise<SaveSummaryDto> {
  return apiFetch<SaveSummaryDto>('/saves', { method: 'POST', body: request });
}

export function deleteSave(saveId: number): Promise<void> {
  return apiFetch<void>(`/saves/${saveId}`, { method: 'DELETE' });
}
