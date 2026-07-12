import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deposit, withdraw } from '../../api/bank';
import { errorMessage } from '../../api/http';
import type { PanelProps } from './types';

type Operation = 'deposit' | 'withdraw';

function parseAmount(raw: string): number | null {
  const trimmed = raw.trim();
  if (!/^\d+$/.test(trimmed)) {
    return null;
  }
  const amount = Number(trimmed);
  return amount > 0 ? amount : null;
}

export function BankPanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const [amountInput, setAmountInput] = useState('');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: ({ operation, amount }: { operation: Operation; amount: number }) =>
      operation === 'deposit' ? deposit(saveId, amount) : withdraw(saveId, amount),
    onSuccess: (res, variables) => {
      queryClient.setQueryData(['save', saveId], res.state);
      setAmountInput('');
      setError(null);
      onNotify(
        variables.operation === 'deposit'
          ? `Deposited R${variables.amount}`
          : `Withdrew R${variables.amount}`,
      );
    },
    onError: (err: unknown) => {
      setError(errorMessage(err));
      // A rejected bank action can still be charged/settled server-side, so never
      // leave a stale cached balance around.
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  const handleSubmit = (operation: Operation) => {
    const amount = parseAmount(amountInput);
    if (amount === null) {
      setError('Enter a positive whole amount.');
      return;
    }
    if (operation === 'deposit' && amount > player.cash) {
      setError(`You only have R${player.cash} in cash.`);
      return;
    }
    if (operation === 'withdraw' && amount > player.bank) {
      setError(`You only have R${player.bank} in the bank.`);
      return;
    }
    setError(null);
    mutation.mutate({ operation, amount });
  };

  return (
    <div className="bank-panel">
      <h2>Bank</h2>
      <p>Bank: R{player.bank}</p>
      <label htmlFor="bank-amount">Amount</label>
      <input
        id="bank-amount"
        inputMode="numeric"
        value={amountInput}
        onChange={(e) => setAmountInput(e.target.value)}
        disabled={mutation.isPending}
      />
      <div className="bank-panel-actions">
        <button type="button" onClick={() => handleSubmit('deposit')} disabled={mutation.isPending}>
          Deposit
        </button>
        <button
          type="button"
          onClick={() => handleSubmit('withdraw')}
          disabled={mutation.isPending}
        >
          Withdraw
        </button>
      </div>
      {error !== null && <p role="alert">{error}</p>}
    </div>
  );
}
