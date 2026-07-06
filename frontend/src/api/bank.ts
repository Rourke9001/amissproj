import { apiFetch } from './http';
import type { BankTransactionResponse } from './types';

export function deposit(username: string, amount: number): Promise<BankTransactionResponse> {
  return apiFetch<BankTransactionResponse>(
    `/players/${encodeURIComponent(username)}/bank/deposit`,
    {
      method: 'POST',
      body: { amount },
    },
  );
}

export function withdraw(username: string, amount: number): Promise<BankTransactionResponse> {
  return apiFetch<BankTransactionResponse>(
    `/players/${encodeURIComponent(username)}/bank/withdraw`,
    {
      method: 'POST',
      body: { amount },
    },
  );
}
