import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyAppliance, getApplianceCatalog } from '../../api/appliances';
import { errorMessage } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { ApplianceResponse } from '../../api/types';
import type { PanelProps } from './types';

const BOOK_NAMES: Record<string, string> = {
  ENCYCLOPEDIA: 'Encyclopedia',
  DICTIONARY: 'Dictionary',
  ATLAS: 'Atlas',
};

export function ZMartPanel({ saveId, onNotify }: PanelProps) {
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
      const name = BOOK_NAMES[res.item] ?? res.item;
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

  const stock = catalogQuery.data?.filter((item) => item.store === 'Z_MART');
  if (stock === undefined) {
    return null;
  }

  const rows: StoreRow[] = stock.map((item) => ({
    id: item.id,
    label: BOOK_NAMES[item.id] ?? item.id,
    price: item.price,
    detail: item.owned ? 'Owned' : undefined,
    actionLabel: 'Buy',
    disabled: item.owned,
  }));

  return (
    <StorePanel
      heading="Z-Mart"
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
