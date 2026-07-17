import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyAppliance, getApplianceCatalog } from '../../api/appliances';
import { errorMessage } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { ApplianceResponse } from '../../api/types';
import type { PanelProps } from './types';

// Item ids are the ApplianceItem enum names on the wire; PR 5 adds COMPUTER here
// when extra credit ships, requiring no change beyond this map.
const APPLIANCE_NAMES: Record<string, string> = {
  FRIDGE: 'Refrigerator',
  FREEZER: 'Freezer',
};

export function SocketCityPanel({ saveId, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const catalogQuery = useQuery({
    queryKey: ['appliances', saveId],
    queryFn: () => getApplianceCatalog(saveId),
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (item: string) => buyAppliance(saveId, item),
    onSuccess: (res: ApplianceResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      void queryClient.invalidateQueries({ queryKey: ['appliances', saveId] });
      const name = APPLIANCE_NAMES[res.item] ?? res.item;
      onNotify(`Bought ${name} (R${res.price})`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  if (catalogQuery.isPending) {
    return <p>Loading stock…</p>;
  }
  if (catalogQuery.error !== null) {
    return <p role="alert">{catalogQuery.error.message}</p>;
  }

  const stock = catalogQuery.data?.filter((item) => item.store === 'SOCKET_CITY');
  if (stock === undefined) {
    return null;
  }

  const rows: StoreRow[] = stock.map((item) => ({
    id: item.id,
    label: APPLIANCE_NAMES[item.id] ?? item.id,
    price: item.price,
    detail: item.owned ? 'Owned' : undefined,
    actionLabel: 'Buy',
  }));

  return (
    <StorePanel
      heading="Socket City"
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
