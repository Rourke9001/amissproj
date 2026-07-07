import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyGroceries, getFoodCatalog } from '../../api/food';
import { ApiError } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { GroceriesResponse } from '../../api/types';
import type { PanelProps } from './types';

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.problem.detail ?? err.problem.title ?? err.message;
  }
  return 'Could not reach the server.';
}

export function BlacksMarketPanel({ username, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const foodQuery = useQuery({ queryKey: ['food'], queryFn: getFoodCatalog, staleTime: Infinity });

  const mutation = useMutation({
    mutationFn: (pack: string) => buyGroceries(username, pack),
    onSuccess: (res: GroceriesResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      const name = foodQuery.data?.packs.find((pack) => pack.id === res.pack)?.name ?? res.pack;
      onNotify(`Bought ${name} (R${res.price}) — ${res.foodWeeks} wk stored`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
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
