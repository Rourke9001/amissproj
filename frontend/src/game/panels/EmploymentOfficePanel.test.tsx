import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EmploymentOfficePanel } from './EmploymentOfficePanel';
import { applyForJob, getJobs } from '../../api/jobs';
import type { ApplyResponse, JobListingDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/jobs', () => ({ getJobs: vi.fn(), applyForJob: vi.fn() }));

function playerFixture(overrides: Partial<SaveStateDto> = {}): SaveStateDto {
  return {
    id: 1,
    label: 'Save 1',
    round: 1,
    timeMinutes: 3600,
    timeDisplay: '60h',
    weekOver: false,
    cash: 100,
    bank: 0,
    debt: 0,
    rentDue: false,
    foodWeeks: 0,
    ateFastFoodLastTurn: false,
    clothingCasualWeeks: 1,
    clothingDressWeeks: 0,
    clothingBusinessWeeks: 0,
    relaxation: 10,
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    location: { id: 'EMPLOYMENT_OFFICE', name: 'Employment Office', ringIndex: 7, row: 3, col: 1 },
    degreesEarned: [],
    currentCourse: null,
    goals: {
      wealth: { current: 1, target: 100, met: false },
      happiness: { current: 0, target: 29, met: false },
      education: { current: 1, target: 15, met: false },
      career: { current: 0, target: 39, met: false },
    },
    won: false,
    ...overrides,
  };
}

const JOB_LISTINGS: JobListingDto[] = [
  { id: 4, name: 'Cook', location: 'Monolith Burgers', wage: 5 },
  { id: 5, name: 'Clerk', location: 'Monolith Burgers', wage: 6 },
  { id: 1, name: 'Clerk', location: 'Z-Mart', wage: 5 },
];

function renderPanel(saveId = 1) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const onNotify = vi.fn();
  render(
    <QueryClientProvider client={queryClient}>
      <EmploymentOfficePanel saveId={saveId} player={playerFixture()} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { queryClient, invalidateSpy, onNotify };
}

describe('EmploymentOfficePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(getJobs).mockResolvedValue(JOB_LISTINGS);
  });

  it('lists workplaces with no requirement/experience/dependability text anywhere', async () => {
    renderPanel();
    expect(await screen.findByRole('button', { name: 'Monolith Burgers' })).toBeInTheDocument();
    expect(getJobs).toHaveBeenCalledWith(1);
    expect(screen.getByRole('button', { name: 'Z-Mart' })).toBeInTheDocument();
    expect(screen.queryByText(/experience/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/dependability/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/require/i)).not.toBeInTheDocument();
  });

  it('drills into a workplace: shows only name + wage, every Apply button enabled', async () => {
    renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));

    expect(screen.getByText('Cook')).toBeInTheDocument();
    expect(screen.getByText('R5/h')).toBeInTheDocument();
    const applyButtons = screen.getAllByRole('button', { name: 'Apply' });
    expect(applyButtons).toHaveLength(2);
    applyButtons.forEach((button) => expect(button).toBeEnabled());
  });

  it('reports a hire with the wage and writes the fresh save state into the cache', async () => {
    const response: ApplyResponse = {
      hired: true,
      reasons: [],
      minutesCharged: 240,
      job: 'Cook',
      wage: 5,
      state: playerFixture({ job: { name: 'Cook', hourlyWage: 5, location: 'Monolith Burgers' } }),
    };
    vi.mocked(applyForJob).mockResolvedValue(response);
    const { onNotify, queryClient } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Hired as Cook at R5/h.'));
    expect(applyForJob).toHaveBeenCalledWith(1, 4);
    expect(queryClient.getQueryData(['save', 1])).toEqual(response.state);
  });

  it('renders officer-style copy for a multi-reason rejection', async () => {
    const response: ApplyResponse = {
      hired: false,
      reasons: ['NOT_ENOUGH_EXPERIENCE', 'POOR_WORK_HISTORY'],
      minutesCharged: 240,
      job: 'Cook',
      wage: null,
      state: playerFixture(),
    };
    vi.mocked(applyForJob).mockResolvedValue(response);
    const { onNotify } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith(
        "You don't have enough work experience for this position. Your work history doesn't meet our standards for this position.",
      ),
    );
  });

  it('renders No Openings copy for that specific rejection reason', async () => {
    const response: ApplyResponse = {
      hired: false,
      reasons: ['NO_OPENINGS'],
      minutesCharged: 240,
      job: 'Cook',
      wage: null,
      state: playerFixture(),
    };
    vi.mocked(applyForJob).mockResolvedValue(response);
    const { onNotify } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith(
        'Sorry, there are no openings right now, try again another day.',
      ),
    );
  });

  it('invalidates save state and reports the error on a network/server failure', async () => {
    vi.mocked(applyForJob).mockRejectedValue(new Error('week over'));
    const { invalidateSpy, onNotify } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 1] }));
    expect(onNotify).toHaveBeenCalledWith('Could not reach the server.');
  });
});
