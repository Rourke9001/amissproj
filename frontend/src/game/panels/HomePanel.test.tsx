import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HomePanel } from './HomePanel';
import { relax } from '../../api/home';
import { ApiError } from '../../api/http';
import type { SaveStateDto } from '../../api/types';

vi.mock('../../api/home', () => ({
  relax: vi.fn(),
}));

const relaxMock = vi.mocked(relax);

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
    clothingCasualWeeks: 6,
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
    location: { id: 'LOW_COST_HOUSING', name: 'Low-Cost Housing', ringIndex: 0, row: 3, col: 2 },
    ...overrides,
  };
}

function renderPanel(player: SaveStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <HomePanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, onNotify };
}

describe('HomePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the Home heading and the food-stored line', () => {
    renderPanel(playerFixture({ foodWeeks: 3 }));

    expect(screen.getByRole('heading', { name: 'Home' })).toBeInTheDocument();
    expect(screen.getByText('Food stored: 3 wk')).toBeInTheDocument();
  });

  it('shows the rent-due line when rent is due', () => {
    renderPanel(playerFixture({ rentDue: true }));

    expect(
      screen.getByText('Rent is due — the Rent Office expects R80 this round.'),
    ).toBeInTheDocument();
  });

  it('omits the rent-due line when rent is not due', () => {
    renderPanel(playerFixture({ rentDue: false }));

    expect(
      screen.queryByText('Rent is due — the Rent Office expects R80 this round.'),
    ).not.toBeInTheDocument();
  });

  it('shows the current relaxation stat', () => {
    renderPanel(playerFixture({ relaxation: 34 }));
    expect(screen.getByText('Relaxation: 34 / 50')).toBeInTheDocument();
  });

  it('relaxes successfully and updates the save-query cache', async () => {
    relaxMock.mockResolvedValue({
      minutesCharged: 360,
      relaxation: 13,
      state: playerFixture({ relaxation: 13 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    await user.click(screen.getByRole('button', { name: 'Relax' }));

    expect(relaxMock).toHaveBeenCalledWith(42);
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Relaxed — Relaxation now 13'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ relaxation: 13 });
  });

  it('renders the problem detail on an ApiError and invalidates the save query', async () => {
    relaxMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at Low-Cost Housing.',
      }),
    );
    const user = userEvent.setup();
    renderPanel(playerFixture());

    await user.click(screen.getByRole('button', { name: 'Relax' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at Low-Cost Housing.');
  });
});
