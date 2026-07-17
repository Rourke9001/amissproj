import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SocketCityPanel } from './SocketCityPanel';
import { buyAppliance, getApplianceCatalog } from '../../api/appliances';
import { ApiError } from '../../api/http';
import type { ApplianceDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/appliances', () => ({
  getApplianceCatalog: vi.fn(),
  buyAppliance: vi.fn(),
}));

const getApplianceCatalogMock = vi.mocked(getApplianceCatalog);
const buyApplianceMock = vi.mocked(buyAppliance);

const CATALOG_FIXTURE: ApplianceDto[] = [
  { id: 'FRIDGE', price: 876, store: 'SOCKET_CITY', owned: false },
  { id: 'FREEZER', price: 513, store: 'SOCKET_CITY', owned: true },
  { id: 'ENCYCLOPEDIA', price: 475, store: 'Z_MART', owned: false },
];

function playerFixture(): SaveStateDto {
  return {
    id: 42,
    label: 'Save 42',
    round: 3,
    timeMinutes: 3600,
    timeDisplay: '72h',
    weekOver: false,
    cash: 2000,
    bank: 100,
    debt: 0,
    rentDue: false,
    foodWeeks: 0,
    ateFastFoodLastTurn: false,
    clothing: 1,
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    degreesEarned: [],
    currentCourse: null,
    goals: {
      wealth: { current: 500, target: 5000, met: false },
      happiness: { current: 50, target: 100, met: false },
      education: { current: 0, target: 100, met: false },
      career: { current: 0, target: 10, met: false },
    },
    won: false,
    location: { id: 'SOCKET_CITY', name: 'Socket City', ringIndex: 5, row: 1, col: 0 },
  };
}

function renderPanel(onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <SocketCityPanel saveId={42} player={playerFixture()} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('SocketCityPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getApplianceCatalogMock.mockResolvedValue(CATALOG_FIXTURE);
  });

  it('renders only Socket City items, filtering out Z-Mart stock', async () => {
    renderPanel();

    expect(await screen.findByText('Refrigerator')).toBeInTheDocument();
    expect(screen.getByText('Freezer')).toBeInTheDocument();
    expect(screen.queryByText(/ENCYCLOPEDIA/i)).not.toBeInTheDocument();
  });

  it('shows Owned on an owned item', async () => {
    renderPanel();

    const freezerRow = (await screen.findByText('Freezer')).closest('li') as HTMLElement;
    expect(within(freezerRow).getByText('Owned')).toBeInTheDocument();
  });

  it('disables the Buy button on an owned item, leaving an unowned item clickable', async () => {
    renderPanel();

    const freezerRow = (await screen.findByText('Freezer')).closest('li') as HTMLElement;
    expect(within(freezerRow).getByRole('button', { name: 'Buy' })).toBeDisabled();

    const fridgeRow = (await screen.findByText('Refrigerator')).closest('li') as HTMLElement;
    expect(within(fridgeRow).getByRole('button', { name: 'Buy' })).toBeEnabled();
  });

  it('buys successfully and updates the save-query cache', async () => {
    buyApplianceMock.mockResolvedValue({
      item: 'FRIDGE',
      price: 876,
      state: { ...playerFixture(), cash: 1124 },
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel();

    const fridgeRow = (await screen.findByText('Refrigerator')).closest('li') as HTMLElement;
    await user.click(within(fridgeRow).getByRole('button', { name: 'Buy' }));

    expect(buyApplianceMock).toHaveBeenCalledWith(42, 'FRIDGE');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Bought Refrigerator (R876)'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 1124 });
  });

  it('renders the problem detail inline on an ApiError and invalidates the save query', async () => {
    buyApplianceMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at Socket City.',
      }),
    );
    const user = userEvent.setup();
    const { invalidateSpy } = renderPanel();

    const fridgeRow = (await screen.findByText('Refrigerator')).closest('li') as HTMLElement;
    await user.click(within(fridgeRow).getByRole('button', { name: 'Buy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at Socket City.');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
