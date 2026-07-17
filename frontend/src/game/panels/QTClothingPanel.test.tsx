import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { QTClothingPanel } from './QTClothingPanel';
import { buyClothes, getClothesCatalog } from '../../api/clothes';
import { ApiError } from '../../api/http';
import type { ClothingItemDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/clothes', () => ({
  getClothesCatalog: vi.fn(),
  buyClothes: vi.fn(),
}));

const getClothesCatalogMock = vi.mocked(getClothesCatalog);
const buyClothesMock = vi.mocked(buyClothes);

const CATALOG_FIXTURE: ClothingItemDto[] = [
  { id: 'CASUAL', name: 'Casual Clothes', price: 20, level: 1 },
  { id: 'FORMAL', name: 'Formal Clothes', price: 35, level: 2 },
  { id: 'SUIT', name: 'Suit', price: 55, level: 3 },
];

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
    location: { id: 'QT_CLOTHING', name: 'QT Clothing', ringIndex: 4, row: 2, col: 4 },
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
      <QTClothingPanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('QTClothingPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getClothesCatalogMock.mockResolvedValue(CATALOG_FIXTURE);
  });

  it('renders the stock with level details', async () => {
    renderPanel(playerFixture({ clothing: 0 }));

    expect(getClothesCatalogMock).toHaveBeenCalledWith(42);
    expect(await screen.findByText('Casual Clothes')).toBeInTheDocument();
    expect(screen.getByText('R20')).toBeInTheDocument();
    expect(screen.getByText('Level 1')).toBeInTheDocument();
    expect(screen.getByText('Formal Clothes')).toBeInTheDocument();
    expect(screen.getByText('Level 2')).toBeInTheDocument();
    expect(screen.getByText('Suit')).toBeInTheDocument();
    expect(screen.getByText('Level 3')).toBeInTheDocument();
  });

  it('marks the current level as (current) and disables it', async () => {
    renderPanel(playerFixture({ clothing: 2 }));

    const formalRow = (await screen.findByText('Formal Clothes')).closest('li') as HTMLElement;
    expect(within(formalRow).getByText('Level 2 (current)')).toBeInTheDocument();
    expect(within(formalRow).getByRole('button', { name: 'Buy' })).toBeDisabled();
  });

  it('disables a level lower than the current level', async () => {
    renderPanel(playerFixture({ clothing: 2 }));

    const casualRow = (await screen.findByText('Casual Clothes')).closest('li') as HTMLElement;
    expect(within(casualRow).getByRole('button', { name: 'Buy' })).toBeDisabled();
  });

  it('leaves a higher level enabled', async () => {
    renderPanel(playerFixture({ clothing: 1 }));

    const suitRow = (await screen.findByText('Suit')).closest('li') as HTMLElement;
    expect(within(suitRow).getByRole('button', { name: 'Buy' })).not.toBeDisabled();
  });

  it('buys clothes successfully, calls the api with the item id, and notifies with the new level', async () => {
    buyClothesMock.mockResolvedValue({
      item: 'FORMAL',
      price: 35,
      clothingLevel: 2,
      state: playerFixture({ cash: 465, clothing: 2 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture({ clothing: 1 }));

    const formalRow = (await screen.findByText('Formal Clothes')).closest('li') as HTMLElement;
    await user.click(within(formalRow).getByRole('button', { name: 'Buy' }));

    expect(buyClothesMock).toHaveBeenCalledWith(42, 'FORMAL');
    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Bought Formal Clothes (R35) — clothing level 2'),
    );
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 465 });
  });

  it('renders the problem detail inline on an ApiError and invalidates the player query', async () => {
    buyClothesMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at QT Clothing.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderPanel(playerFixture({ clothing: 1 }));

    const suitRow = (await screen.findByText('Suit')).closest('li') as HTMLElement;
    await user.click(within(suitRow).getByRole('button', { name: 'Buy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at QT Clothing.');
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
