import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BlacksMarketPanel } from './BlacksMarketPanel';
import { buyGroceries, getFoodCatalog } from '../../api/food';
import { ApiError } from '../../api/http';
import type { FoodCatalogDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/food', () => ({
  getFoodCatalog: vi.fn(),
  eat: vi.fn(),
  buyGroceries: vi.fn(),
}));

const getFoodCatalogMock = vi.mocked(getFoodCatalog);
const buyGroceriesMock = vi.mocked(buyGroceries);

const CATALOG_FIXTURE: FoodCatalogDto = {
  menu: [{ id: 'BURGER', name: 'Burger', price: 32 }],
  packs: [
    { id: 'ONE_WEEK', name: '1 Weeks of Food', price: 25, weeks: 1 },
    { id: 'TWO_WEEK', name: '2 Weeks of Food', price: 45, weeks: 2 },
  ],
};

function playerFixture(overrides: Partial<SaveStateDto> = {}): SaveStateDto {
  return {
    id: 42,
    label: 'Save 42',
    round: 3,
    timeMinutes: 4320,
    timeDisplay: '72h',
    weekOver: false,
    cash: 500,
    bank: 100,
    debt: 0,
    rentDue: false,
    foodWeeks: 0,
    ateFastFoodLastTurn: false,
    clothingCasualWeeks: 1,
    clothingDressWeeks: 0,
    clothingBusinessWeeks: 0,
    relaxation: 10,
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
    location: { id: 'BLACKS_MARKET', name: "Black's Market", ringIndex: 10, row: 1, col: 0 },
    ...overrides,
  };
}

function renderPanel(player: SaveStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <BlacksMarketPanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('BlacksMarketPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getFoodCatalogMock.mockResolvedValue(CATALOG_FIXTURE);
  });

  it('renders the grocery packs with their price and weeks detail', async () => {
    renderPanel(playerFixture());

    expect(getFoodCatalogMock).toHaveBeenCalledWith(42);
    expect(await screen.findByText('1 Weeks of Food')).toBeInTheDocument();
    expect(screen.getByText('R25')).toBeInTheDocument();
    expect(screen.getByText('+1 wk')).toBeInTheDocument();

    expect(screen.getByText('2 Weeks of Food')).toBeInTheDocument();
    expect(screen.getByText('R45')).toBeInTheDocument();
    expect(screen.getByText('+2 wk')).toBeInTheDocument();
  });

  it('buys a pack successfully, calls the api with the pack id, and notifies with the stored total', async () => {
    buyGroceriesMock.mockResolvedValue({
      pack: 'TWO_WEEK',
      price: 45,
      weeksAdded: 2,
      foodWeeks: 2,
      state: playerFixture({ cash: 455, foodWeeks: 2 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    const row = (await screen.findByText('2 Weeks of Food')).closest('li') as HTMLElement;
    await user.click(within(row).getByRole('button', { name: 'Buy' }));

    expect(buyGroceriesMock).toHaveBeenCalledWith(42, 'TWO_WEEK');
    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Bought 2 Weeks of Food (R45) — 2 wk stored'),
    );
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 455 });
  });

  it('renders the problem detail inline on a 409 insufficient-funds error and invalidates the player query', async () => {
    buyGroceriesMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:insufficient-funds',
        title: 'Conflict',
        status: 409,
        detail: 'Not enough cash for that pack.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderPanel(playerFixture());

    const row = (await screen.findByText('1 Weeks of Food')).closest('li') as HTMLElement;
    await user.click(within(row).getByRole('button', { name: 'Buy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Not enough cash for that pack.');
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
