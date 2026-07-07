import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyClothes, getClothesCatalog } from '../../api/clothes';
import { ApiError } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { ClothesResponse } from '../../api/types';
import type { PanelProps } from './types';

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.problem.detail ?? err.problem.title ?? err.message;
  }
  return 'Could not reach the server.';
}

export function QTClothingPanel({ username, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const clothesQuery = useQuery({
    queryKey: ['clothes'],
    queryFn: getClothesCatalog,
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (item: string) => buyClothes(username, item),
    onSuccess: (res: ClothesResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      const name = clothesQuery.data?.find((item) => item.id === res.item)?.name ?? res.item;
      onNotify(`Bought ${name} (R${res.price}) — clothing level ${res.clothingLevel}`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
    },
  });

  if (clothesQuery.isPending) {
    return <p>Loading stock…</p>;
  }
  if (clothesQuery.error !== null) {
    return <p role="alert">{clothesQuery.error.message}</p>;
  }

  const stock = clothesQuery.data;
  if (stock === undefined) {
    return null;
  }

  // Buying only ever upgrades: no point re-selling your current level or a
  // lower one, so those rows stay visible but disabled.
  const rows: StoreRow[] = stock.map((item) => {
    const isCurrent = player.clothing === item.level;
    return {
      id: item.id,
      label: item.name,
      price: item.price,
      detail: isCurrent ? `Level ${item.level} (current)` : `Level ${item.level}`,
      actionLabel: 'Buy',
      disabled: player.clothing >= item.level,
    };
  });

  return (
    <StorePanel
      heading="QT Clothing"
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
