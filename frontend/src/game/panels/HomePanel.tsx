import { useMutation, useQueryClient } from '@tanstack/react-query';
import { relax } from '../../api/home';
import { errorMessage } from '../../api/http';
import type { RelaxResponse } from '../../api/types';
import type { PanelProps } from './types';

export function HomePanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => relax(saveId),
    onSuccess: (res: RelaxResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      onNotify(`Relaxed — Relaxation now ${res.relaxation}`);
    },
    onError: (err: unknown) => {
      onNotify(errorMessage(err));
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  return (
    <div className="home-panel">
      <h2>Home</h2>
      <p>Food stored: {player.foodWeeks} wk</p>
      {player.rentDue && <p>Rent is due — the Rent Office expects R80 this round.</p>}
      <p>Relaxation: {player.relaxation} / 50</p>
      <button type="button" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
        Relax
      </button>
      {mutation.error !== null && <p role="alert">{errorMessage(mutation.error)}</p>}
    </div>
  );
}
