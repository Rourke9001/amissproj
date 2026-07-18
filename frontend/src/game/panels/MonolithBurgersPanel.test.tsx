import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MonolithBurgersPanel } from './MonolithBurgersPanel';
import { eat, getFoodCatalog } from '../../api/food';
import { ApiError } from '../../api/http';
import type { FoodCatalogDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/food', () => ({
  getFoodCatalog: vi.fn(),
  eat: vi.fn(),
  buyGroceries: vi.fn(),
}));

const getFoodCatalogMock = vi.mocked(getFoodCatalog);
const eatMock = vi.mocked(eat);

const CATALOG_FIXTURE: FoodCatalogDto = {
  menu: [
    { id: 'BURGER', name: 'Burger', price: 32 },
    { id: 'PIZZA', name: 'Pizza', price: 45 },
  ],
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
    foodWeeks: 2,
    ateFastFoodLastTurn: false,
    clothingCasualWeeks: 1,
    clothingDressWeeks: 0,
    clothingBusinessWeeks: 0,
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
    location: { id: 'MONOLITH_BURGERS', name: 'Monolith Burgers', ringIndex: 3, row: 1, col: 4 },
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
      <MonolithBurgersPanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('MonolithBurgersPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getFoodCatalogMock.mockResolvedValue(CATALOG_FIXTURE);
  });

  it('renders the menu from the catalog', async () => {
    renderPanel(playerFixture());

    expect(getFoodCatalogMock).toHaveBeenCalledWith(42);
    expect(await screen.findByText('Burger')).toBeInTheDocument();
    expect(screen.getByText('R32')).toBeInTheDocument();
    expect(screen.getByText('Pizza')).toBeInTheDocument();
    expect(screen.getByText('R45')).toBeInTheDocument();
  });

  it('shows the fed status line when the player has food stored', async () => {
    renderPanel(playerFixture({ foodWeeks: 1 }));

    expect(
      await screen.findByText('You have food stored for the coming week.'),
    ).toBeInTheDocument();
  });

  it('shows the not-fed status line when the player has no food stored', async () => {
    renderPanel(playerFixture({ foodWeeks: 0 }));

    expect(
      await screen.findByText('No food stored — eat or buy groceries before the week ends.'),
    ).toBeInTheDocument();
  });

  it('shows the fed status line when the player ate fast food even with no food stored', async () => {
    renderPanel(playerFixture({ foodWeeks: 0, ateFastFoodLastTurn: true }));

    expect(
      await screen.findByText('You have food stored for the coming week.'),
    ).toBeInTheDocument();
  });

  it('eats successfully, calls the api with the item id, notifies without a time cost, and updates the cache', async () => {
    eatMock.mockResolvedValue({
      item: 'BURGER',
      price: 32,
      ate: true,
      reason: null,
      minutesCharged: 0, // eating costs no time (reference rule)
      state: playerFixture({ cash: 468, foodWeeks: 1 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    const burgerRow = (await screen.findByText('Burger')).closest('li') as HTMLElement;
    await user.click(within(burgerRow).getByRole('button', { name: 'Eat' }));

    expect(eatMock).toHaveBeenCalledWith(42, 'BURGER');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Ate a Burger (R32)'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 468 });
  });

  it('mentions the time only when a deployment configures an eat cost', async () => {
    eatMock.mockResolvedValue({
      item: 'BURGER',
      price: 32,
      ate: true,
      reason: null,
      minutesCharged: 60, // AMISS_COSTS_EAT_MINUTES override in play
      state: playerFixture({ cash: 468, foodWeeks: 1 }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderPanel(playerFixture());

    const burgerRow = (await screen.findByText('Burger')).closest('li') as HTMLElement;
    await user.click(within(burgerRow).getByRole('button', { name: 'Eat' }));

    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Ate a Burger (R32, 1h)'));
  });

  it('reports an unaffordable eat (ate:false) as an outcome, not an error, and still updates the cache', async () => {
    eatMock.mockResolvedValue({
      item: 'BURGER',
      price: 32,
      ate: false,
      reason: 'INSUFFICIENT_CASH',
      minutesCharged: 0,
      state: playerFixture({ cash: 10 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    const burgerRow = (await screen.findByText('Burger')).closest('li') as HTMLElement;
    await user.click(within(burgerRow).getByRole('button', { name: 'Eat' }));

    await waitFor(() => expect(onNotify).toHaveBeenCalledWith("Couldn't afford the Burger"));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 10 });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders the problem detail inline on a 409 wrong-location error and invalidates the player query', async () => {
    eatMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at Monolith Burgers.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderPanel(playerFixture());

    const burgerRow = (await screen.findByText('Burger')).closest('li') as HTMLElement;
    await user.click(within(burgerRow).getByRole('button', { name: 'Eat' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at Monolith Burgers.');
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
