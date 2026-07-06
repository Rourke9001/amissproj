import { useMutation, useQueryClient } from '@tanstack/react-query';
import { work } from '../api/jobs';
import { ApiError } from '../api/http';
import { formatMinutes } from './formatMinutes';
import type { WorkResponse } from '../api/types';
import type { PanelProps } from './panels/types';

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.problem.detail ?? err.problem.title ?? err.message;
  }
  return 'Could not reach the server.';
}

export function WorkAction({ username, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => work(username),
    onSuccess: (res: WorkResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      // `hourlyWage` is the per-SHIFT payout despite its name (the DTO inherited the
      // ambiguous tbljobs.salary naming; core pays it once per shift, Jones-style).
      onNotify(
        res.debtDocked
          ? `Worked ${formatMinutes(res.minutesCharged)} as ${res.job} — wages went to your debt`
          : `Worked ${formatMinutes(res.minutesCharged)} as ${res.job} — earned R${res.hourlyWage}`,
      );
    },
    onError: (err: unknown) => {
      onNotify(errorMessage(err));
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
    },
  });

  if (player.job === null || player.location.name !== player.job.location) {
    return null;
  }

  return (
    <div className="work-action">
      <button
        type="button"
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        className="work-action-button"
      >
        Work a shift (6h)
      </button>
    </div>
  );
}
