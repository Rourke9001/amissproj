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
  { id: 'CASUAL', name: 'Casual Clothes', price: 73, level: 1, weeks: 11 },
  { id: 'DRESS', name: 'Dress Clothes', price: 125, level: 2, weeks: 13 },
  { id: 'BUSINESS', name: 'Business Suit', price: 295, level: 3, weeks: 13 },
];

function playerFixture(overrides: Partial<SaveStateDto> = {}): SaveStateDto {
  return {
    id: 42,
    label: 'Save 42',
    round: 3,
    timeMinutes: 3600,
    timeDisplay: '72h',
    weekOver: false,
    cash: 500,
    bank: 100,
    debt: 0,
    rentDue: false,
    foodWeeks: 2,
    ateFastFoodLastTurn: false,
    clothingCasualWeeks: 3,
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

  it('shows weeks-remaining per category and keeps every Buy button enabled', async () => {
    renderPanel(playerFixture());

    const casualRow = (await screen.findByText('Casual Clothes')).closest('li') as HTMLElement;
    expect(within(casualRow).getByText('3 wk left, +11 wk on purchase')).toBeInTheDocument();
    expect(within(casualRow).getByRole('button', { name: 'Buy' })).not.toBeDisabled();

    const dressRow = screen.getByText('Dress Clothes').closest('li') as HTMLElement;
    expect(within(dressRow).getByText('0 wk left, +13 wk on purchase')).toBeInTheDocument();
    expect(within(dressRow).getByRole('button', { name: 'Buy' })).not.toBeDisabled();
  });

  it('shows distinct weeks-remaining for each clothing category independently', async () => {
    renderPanel(
      playerFixture({
        clothingCasualWeeks: 4,
        clothingDressWeeks: 7,
        clothingBusinessWeeks: 11,
      }),
    );

    const casualRow = (await screen.findByText('Casual Clothes')).closest('li') as HTMLElement;
    expect(within(casualRow).getByText('4 wk left, +11 wk on purchase')).toBeInTheDocument();

    const dressRow = screen.getByText('Dress Clothes').closest('li') as HTMLElement;
    expect(within(dressRow).getByText('7 wk left, +13 wk on purchase')).toBeInTheDocument();

    const businessRow = screen.getByText('Business Suit').closest('li') as HTMLElement;
    expect(within(businessRow).getByText('11 wk left, +13 wk on purchase')).toBeInTheDocument();
  });

  it('buys clothes successfully and notifies without a level number', async () => {
    buyClothesMock.mockResolvedValue({
      item: 'DRESS',
      price: 125,
      state: playerFixture({ cash: 375, clothingDressWeeks: 13 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    const dressRow = (await screen.findByText('Dress Clothes')).closest('li') as HTMLElement;
    await user.click(within(dressRow).getByRole('button', { name: 'Buy' }));

    expect(buyClothesMock).toHaveBeenCalledWith(42, 'DRESS');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Bought Dress Clothes (R125)'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 375 });
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
    const { onNotify, invalidateSpy } = renderPanel(playerFixture());

    const suitRow = (await screen.findByText('Business Suit')).closest('li') as HTMLElement;
    await user.click(within(suitRow).getByRole('button', { name: 'Buy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at QT Clothing.');
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
