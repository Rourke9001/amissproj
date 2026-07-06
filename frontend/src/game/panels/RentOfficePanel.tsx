import { useMutation, useQueryClient } from '@tanstack/react-query';
import { payRent } from '../../api/rent';
import { ApiError } from '../../api/http';
import { formatMinutes } from '../formatMinutes';
import type { RentPaymentResponse } from '../../api/types';
import type { PanelProps } from './types';

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.problem.detail ?? err.problem.title ?? err.message;
  }
  return 'Could not reach the server.';
}

export function RentOfficePanel({ username, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => payRent(username),
    onSuccess: (res: RentPaymentResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      onNotify(`Paid R${res.amountPaid} rent (${formatMinutes(res.minutesCharged)})`);
    },
    onError: () => {
      // A rejected rent payment can still have charged the clock server-side, so
      // never leave a stale cached state around.
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
    },
  });

  return (
    <div className="rent-office-panel">
      <h2>Rent Office</h2>
      {player.rentDue ? (
        <>
          <p>Rent due: R80.</p>
          <button type="button" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
            Pay Rent
          </button>
        </>
      ) : (
        <p>No rent is due.</p>
      )}
      {mutation.error !== null && <p role="alert">{errorMessage(mutation.error)}</p>}
    </div>
  );
}
