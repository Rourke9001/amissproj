import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyGroceries, getFoodCatalog } from '../../api/food';
import { errorMessage } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { GroceriesResponse } from '../../api/types';
import type { PanelProps } from './types';

export function BlacksMarketPanel({ saveId, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const foodQuery = useQuery({
    queryKey: ['food', saveId],
    queryFn: () => getFoodCatalog(saveId),
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (pack: string) => buyGroceries(saveId, pack),
    onSuccess: (res: GroceriesResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      const name = foodQuery.data?.packs.find((pack) => pack.id === res.pack)?.name ?? res.pack;
      onNotify(`Bought ${name} (R${res.price}) — ${res.foodWeeks} wk stored`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  if (foodQuery.isPending) {
    return <p>Loading grocery packs…</p>;
  }
  if (foodQuery.error !== null) {
    return <p role="alert">{foodQuery.error.message}</p>;
  }

  const catalog = foodQuery.data;
  if (catalog === undefined) {
    return null;
  }

  const rows: StoreRow[] = catalog.packs.map((pack) => ({
    id: pack.id,
    label: pack.name,
    price: pack.price,
    detail: `+${pack.weeks} wk`,
    actionLabel: 'Buy',
  }));

  return (
    <StorePanel
      heading="Black's Market"
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
