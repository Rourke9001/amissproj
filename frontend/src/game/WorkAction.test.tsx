import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WorkAction } from './WorkAction';
import { work } from '../api/jobs';
import { ApiError } from '../api/http';
import type { SaveStateDto } from '../api/types';

vi.mock('../api/jobs', () => ({
  getJobs: vi.fn(),
  applyForJob: vi.fn(),
  work: vi.fn(),
}));

const workMock = vi.mocked(work);

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
    location: { id: 'MONOLITH_BURGERS', name: 'Monolith Burgers', ringIndex: 3, row: 1, col: 4 },
    ...overrides,
  };
}

function renderWorkAction(player: SaveStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <WorkAction saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('WorkAction', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders nothing when the player has no job', () => {
    const { container } = renderWorkAction(
      playerFixture({ job: { name: 'Unemployed', hourlyWage: null, location: null } }),
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when the job location does not match the current stop', () => {
    const { container } = renderWorkAction(
      playerFixture({
        job: { name: 'Cook', hourlyWage: 6, location: 'Socket City' },
        location: {
          id: 'MONOLITH_BURGERS',
          name: 'Monolith Burgers',
          ringIndex: 3,
          row: 1,
          col: 4,
        },
      }),
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a Work button when the job location matches the current stop', () => {
    renderWorkAction(
      playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    );
    expect(screen.getByRole('button', { name: 'Work a shift (6h)' })).toBeInTheDocument();
  });

  it('works a shift and notifies with the amount earned when nothing is garnished', async () => {
    workMock.mockResolvedValue({
      status: 'OK',
      warning: false,
      job: 'Cook',
      pay: 36,
      netPaid: 36,
      garnished: 0,
      minutesCharged: 360,
      state: playerFixture({
        job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' },
        cash: 536,
      }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderWorkAction(
      playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    );

    await user.click(screen.getByRole('button', { name: 'Work a shift (6h)' }));

    expect(workMock).toHaveBeenCalledWith(42);
    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Worked a shift as Cook: earned R36.'),
    );
  });

  it('notifies with the garnished amount, net pay, and a final-warning suffix when docked', async () => {
    workMock.mockResolvedValue({
      status: 'OK',
      warning: true,
      job: 'Cook',
      pay: 36,
      netPaid: 21,
      garnished: 15,
      minutesCharged: 360,
      state: playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderWorkAction(
      playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    );

    await user.click(screen.getByRole('button', { name: 'Work a shift (6h)' }));

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith(
        'Worked a shift as Cook: earned R36, R15 garnished for debt (net R21). Final warning.',
      ),
    );
  });

  it('notifies that the player was fired when status is FIRED', async () => {
    workMock.mockResolvedValue({
      status: 'FIRED',
      warning: false,
      job: 'Cook',
      pay: 0,
      netPaid: 0,
      garnished: 0,
      minutesCharged: 360,
      state: playerFixture({ job: { name: 'Unemployed', hourlyWage: null, location: null } }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderWorkAction(
      playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    );

    await user.click(screen.getByRole('button', { name: 'Work a shift (6h)' }));

    await waitFor(() => expect(onNotify).toHaveBeenCalledWith("You've been fired from Cook."));
  });

  it('pushes the problem detail to the feed via onNotify and invalidates on error', async () => {
    workMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:underdressed',
        title: 'Conflict',
        status: 409,
        detail: 'You need better clothing for this job.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderWorkAction(
      playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    );

    await user.click(screen.getByRole('button', { name: 'Work a shift (6h)' }));

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('You need better clothing for this job.'),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
