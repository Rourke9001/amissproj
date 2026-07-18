import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyClothes, getClothesCatalog } from '../../api/clothes';
import { errorMessage } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { ClothesResponse, SaveStateDto } from '../../api/types';
import type { PanelProps } from './types';

function weeksFor(player: SaveStateDto, itemId: string): number {
  switch (itemId) {
    case 'CASUAL':
      return player.clothingCasualWeeks;
    case 'DRESS':
      return player.clothingDressWeeks;
    case 'BUSINESS':
      return player.clothingBusinessWeeks;
    default:
      return 0;
  }
}

export function QTClothingPanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const clothesQuery = useQuery({
    queryKey: ['clothes', saveId],
    queryFn: () => getClothesCatalog(saveId),
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (item: string) => buyClothes(saveId, item),
    onSuccess: (res: ClothesResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      const name = clothesQuery.data?.find((item) => item.id === res.item)?.name ?? res.item;
      onNotify(`Bought ${name} (R${res.price})`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
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

  const rows: StoreRow[] = stock.map((item) => ({
    id: item.id,
    label: item.name,
    price: item.price,
    detail: `${weeksFor(player, item.id)} wk left, +${item.weeks} wk on purchase`,
    actionLabel: 'Buy',
  }));

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
