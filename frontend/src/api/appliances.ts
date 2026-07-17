import { apiFetch } from './http';
import type { ApplianceDto, ApplianceResponse } from './types';

export function getApplianceCatalog(saveId: number): Promise<ApplianceDto[]> {
  return apiFetch<ApplianceDto[]>(`/saves/${saveId}/appliances`);
}

export function buyAppliance(saveId: number, item: string): Promise<ApplianceResponse> {
  return apiFetch<ApplianceResponse>(`/saves/${saveId}/appliances`, {
    method: 'POST',
    body: { item },
  });
}
