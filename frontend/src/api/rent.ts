import { apiFetch } from './http';
import type { RentPaymentResponse } from './types';

export function payRent(username: string): Promise<RentPaymentResponse> {
  return apiFetch<RentPaymentResponse>(`/players/${encodeURIComponent(username)}/rent/pay`, {
    method: 'POST',
  });
}
