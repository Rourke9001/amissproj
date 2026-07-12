import { apiFetch } from './http';
import type { BankTransactionResponse } from './types';

export function deposit(saveId: number, amount: number): Promise<BankTransactionResponse> {
  return apiFetch<BankTransactionResponse>(`/saves/${saveId}/bank/deposit`, {
    method: 'POST',
    body: { amount },
  });
}

export function withdraw(saveId: number, amount: number): Promise<BankTransactionResponse> {
  return apiFetch<BankTransactionResponse>(`/saves/${saveId}/bank/withdraw`, {
    method: 'POST',
    body: { amount },
  });
}
