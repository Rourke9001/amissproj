import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { eat, getFoodCatalog } from '../../api/food';
import { errorMessage } from '../../api/http';
import { formatMinutes } from '../formatMinutes';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { EatResponse } from '../../api/types';
import type { PanelProps } from './types';

export function MonolithBurgersPanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const foodQuery = useQuery({
    queryKey: ['food', saveId],
    queryFn: () => getFoodCatalog(saveId),
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (item: string) => eat(saveId, item),
    onSuccess: (res: EatResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      const name = foodQuery.data?.menu.find((item) => item.id === res.item)?.name ?? res.item;
      // Eating is free by default (purchases cost no time in the reference game);
      // only mention time when a deployment configures an eat cost.
      const charged = res.minutesCharged > 0 ? `, ${formatMinutes(res.minutesCharged)}` : '';
      onNotify(
        res.ate
          ? `Ate a ${name} (R${res.price}${charged})`
          : `Couldn't afford the ${name}${
              res.minutesCharged > 0 ? ` (${formatMinutes(res.minutesCharged)} spent)` : ''
            }`,
      );
    },
    onError: () => {
      // Standing rule: this API charges on some rejection paths, so refetch
      // rather than trust the cached state after any error.
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  if (foodQuery.isPending) {
    return <p>Loading menu…</p>;
  }
  if (foodQuery.error !== null) {
    return <p role="alert">{foodQuery.error.message}</p>;
  }

  const catalog = foodQuery.data;
  if (catalog === undefined) {
    return null;
  }

  const rows: StoreRow[] = catalog.menu.map((item) => ({
    id: item.id,
    label: item.name,
    price: item.price,
    actionLabel: 'Eat',
  }));

  const statusLine =
    player.foodWeeks > 0
      ? 'You have food stored for the coming week.'
      : 'No food stored — eat or buy groceries before the week ends.';

  return (
    <StorePanel
      heading="Monolith Burgers"
      statusLine={statusLine}
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
