import { useMutation, useQueryClient } from '@tanstack/react-query';
import { work } from '../api/jobs';
import { errorMessage } from '../api/http';
import type { WorkResponse } from '../api/types';
import type { PanelProps } from './panels/types';

export function WorkAction({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => work(saveId),
    onSuccess: (res: WorkResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      if (res.status === 'FIRED') {
        onNotify(`You've been fired from ${res.job}.`);
      } else if (res.garnished > 0) {
        onNotify(
          `Worked a shift as ${res.job}: earned R${res.pay}, R${res.garnished} garnished for debt (net R${res.netPaid}).${res.warning ? ' Final warning.' : ''}`,
        );
      } else {
        onNotify(
          `Worked a shift as ${res.job}: earned R${res.netPaid}.${res.warning ? ' Final warning.' : ''}`,
        );
      }
    },
    onError: (err: unknown) => {
      onNotify(errorMessage(err));
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  if (player.job.hourlyWage === null || player.location.name !== player.job.location) {
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
