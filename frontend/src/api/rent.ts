import { apiFetch } from './http';
import type { RentPaymentResponse } from './types';

export function payRent(saveId: number): Promise<RentPaymentResponse> {
  return apiFetch<RentPaymentResponse>(`/saves/${saveId}/rent/pay`, { method: 'POST' });
}
