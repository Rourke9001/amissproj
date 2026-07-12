import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RentOfficePanel } from './RentOfficePanel';
import { payRent } from '../../api/rent';
import { ApiError } from '../../api/http';
import type { SaveStateDto } from '../../api/types';

vi.mock('../../api/rent', () => ({
  payRent: vi.fn(),
}));

const payRentMock = vi.mocked(payRent);

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
    rentDue: true,
    foodWeeks: 2,
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
    location: { id: 'RENT_OFFICE', name: 'Rent Office', ringIndex: 12, row: 0, col: 1 },
    ...overrides,
  };
}

function renderRentOfficePanel(player: SaveStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <RentOfficePanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('RentOfficePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the amount due and a Pay Rent button when rent is due', () => {
    renderRentOfficePanel(playerFixture({ rentDue: true }));

    expect(screen.getByText('Rent due: R80.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pay Rent' })).toBeInTheDocument();
  });

  it('shows no button when no rent is due', () => {
    renderRentOfficePanel(playerFixture({ rentDue: false }));

    expect(screen.getByText('No rent is due.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pay Rent' })).not.toBeInTheDocument();
  });

  it('pays rent successfully and notifies with the amount and time charged', async () => {
    payRentMock.mockResolvedValue({
      amountPaid: 80,
      minutesCharged: 120,
      state: playerFixture({ rentDue: false }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderRentOfficePanel(playerFixture({ rentDue: true }));

    await user.click(screen.getByRole('button', { name: 'Pay Rent' }));

    expect(payRentMock).toHaveBeenCalledWith(42);
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Paid R80 rent (2h)'));
  });

  it('renders the problem detail inline on a 409 error and invalidates the player query', async () => {
    payRentMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:insufficient-time',
        title: 'Conflict',
        status: 409,
        detail: 'Not enough time left this week.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderRentOfficePanel(playerFixture({ rentDue: true }));

    await user.click(screen.getByRole('button', { name: 'Pay Rent' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Not enough time left this week.');
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
