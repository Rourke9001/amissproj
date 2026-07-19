import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ZMartPanel } from './ZMartPanel';
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
  { id: 'ENCYCLOPEDIA', price: 475, store: 'Z_MART', owned: false },
  { id: 'DICTIONARY', price: 70, store: 'Z_MART', owned: true },
  { id: 'ATLAS', price: 55, store: 'Z_MART', owned: false },
  { id: 'FRIDGE', price: 876, store: 'SOCKET_CITY', owned: false },
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
    location: { id: 'Z_MART', name: 'Z-Mart', ringIndex: 5, row: 1, col: 0 },
  };
}

function renderPanel(onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <ZMartPanel saveId={42} player={playerFixture()} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('ZMartPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getApplianceCatalogMock.mockResolvedValue(CATALOG_FIXTURE);
  });

  it('renders only Z-Mart items, filtering out Socket City stock', async () => {
    renderPanel();

    expect(await screen.findByText('Encyclopedia')).toBeInTheDocument();
    expect(screen.getByText('Dictionary')).toBeInTheDocument();
    expect(screen.getByText('Atlas')).toBeInTheDocument();
    expect(screen.queryByText(/FRIDGE/i)).not.toBeInTheDocument();
  });

  it('shows Owned on an owned item', async () => {
    renderPanel();

    const dictionaryRow = (await screen.findByText('Dictionary')).closest('li') as HTMLElement;
    expect(within(dictionaryRow).getByText('Owned')).toBeInTheDocument();
  });

  it('disables the Buy button on an owned item, leaving an unowned item clickable', async () => {
    renderPanel();

    const dictionaryRow = (await screen.findByText('Dictionary')).closest('li') as HTMLElement;
    expect(within(dictionaryRow).getByRole('button', { name: 'Buy' })).toBeDisabled();

    const encyclopediaRow = (await screen.findByText('Encyclopedia')).closest('li') as HTMLElement;
    expect(within(encyclopediaRow).getByRole('button', { name: 'Buy' })).toBeEnabled();
  });

  it('buys successfully and updates the save-query cache', async () => {
    buyApplianceMock.mockResolvedValue({
      item: 'ENCYCLOPEDIA',
      price: 475,
      state: { ...playerFixture(), cash: 1525 },
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel();

    const encyclopediaRow = (await screen.findByText('Encyclopedia')).closest('li') as HTMLElement;
    await user.click(within(encyclopediaRow).getByRole('button', { name: 'Buy' }));

    expect(buyApplianceMock).toHaveBeenCalledWith(42, 'ENCYCLOPEDIA');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Bought Encyclopedia (R475)'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 1525 });
  });

  it('renders the problem detail inline on an ApiError and invalidates the save query', async () => {
    buyApplianceMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at Z-Mart.',
      }),
    );
    const user = userEvent.setup();
    const { invalidateSpy } = renderPanel();

    const encyclopediaRow = (await screen.findByText('Encyclopedia')).closest('li') as HTMLElement;
    await user.click(within(encyclopediaRow).getByRole('button', { name: 'Buy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at Z-Mart.');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
