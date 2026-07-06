import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WorkAction } from './WorkAction';
import { work } from '../api/jobs';
import { ApiError } from '../api/http';
import type { PlayerStateDto } from '../api/types';

vi.mock('../api/jobs', () => ({
  getJobs: vi.fn(),
  applyForJob: vi.fn(),
  work: vi.fn(),
}));

const workMock = vi.mocked(work);

function playerFixture(overrides: Partial<PlayerStateDto> = {}): PlayerStateDto {
  return {
    username: 'alice',
    round: 3,
    timeMinutes: 4320,
    timeDisplay: '72h',
    weekOver: false,
    cash: 500,
    bank: 100,
    debt: 0,
    rentDue: false,
    foodWeeks: 2,
    clothing: 1,
    job: null,
    stats: { education: 0, educationProgress: 0, happiness: 50, workExperience: 0 },
    goals: {
      cash: { current: 500, target: 5000 },
      happiness: { current: 50, target: 100 },
      workExperience: { current: 0, target: 10 },
      education: { current: 0, target: 100 },
    },
    location: { id: 'MONOLITH_BURGERS', name: 'Monolith Burgers', ringIndex: 3, row: 1, col: 4 },
    ...overrides,
  };
}

function renderWorkAction(player: PlayerStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <WorkAction username="alice" player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('WorkAction', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders nothing when the player has no job', () => {
    const { container } = renderWorkAction(playerFixture({ job: null }));
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

  it('works a shift and notifies with wage when wages are not docked', async () => {
    workMock.mockResolvedValue({
      job: 'Cook',
      hourlyWage: 6,
      minutesCharged: 360,
      debtDocked: false,
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

    expect(workMock).toHaveBeenCalledWith('alice');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Worked 6h as Cook — earned R6'));
  });

  it('works a shift and notifies that wages went to debt when docked', async () => {
    workMock.mockResolvedValue({
      job: 'Cook',
      hourlyWage: 6,
      minutesCharged: 360,
      debtDocked: true,
      state: playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderWorkAction(
      playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    );

    await user.click(screen.getByRole('button', { name: 'Work a shift (6h)' }));

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Worked 6h as Cook — wages went to your debt'),
    );
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
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['player', 'alice'] });
  });
});
