import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EmploymentOfficePanel } from './EmploymentOfficePanel';
import { applyForJob, getJobs } from '../../api/jobs';
import { ApiError } from '../../api/http';
import type { JobListingDto, PlayerStateDto } from '../../api/types';

vi.mock('../../api/jobs', () => ({
  getJobs: vi.fn(),
  applyForJob: vi.fn(),
  work: vi.fn(),
}));

const getJobsMock = vi.mocked(getJobs);
const applyForJobMock = vi.mocked(applyForJob);

const JOBS_FIXTURE: JobListingDto[] = [
  {
    name: 'Cook',
    requiredEducation: 0,
    hourlyWage: 6,
    location: 'Monolith Burgers',
    requiredClothing: 1,
  },
  {
    name: 'Clerk',
    requiredEducation: 1,
    hourlyWage: 10,
    location: 'Socket City',
    requiredClothing: 2,
  },
];

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
    location: { id: 'EMPLOYMENT_OFFICE', name: 'Employment Office', ringIndex: 7, row: 3, col: 1 },
    ...overrides,
  };
}

function renderPanel(player: PlayerStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <EmploymentOfficePanel username="alice" player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('EmploymentOfficePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getJobsMock.mockResolvedValue(JOBS_FIXTURE);
  });

  it('shows a loading message before the job catalog resolves', () => {
    getJobsMock.mockReturnValue(new Promise(() => {}));
    renderPanel(playerFixture());

    expect(screen.getByText('Loading jobs…')).toBeInTheDocument();
  });

  it('renders each job with its wage and location', async () => {
    renderPanel(playerFixture());

    const cook = await screen.findByText('Cook');
    const cookRow = cook.closest('li') as HTMLElement;
    expect(within(cookRow).getByText('R6/h')).toBeInTheDocument();
    expect(within(cookRow).getByText('Monolith Burgers')).toBeInTheDocument();
  });

  it('marks requirements met/unmet with modifier classes and a "needs" prefix when unmet', async () => {
    renderPanel(
      playerFixture({
        stats: { education: 0, educationProgress: 0, happiness: 50, workExperience: 0 },
        clothing: 2,
      }),
    );

    const clerk = await screen.findByText('Clerk');
    const clerkRow = clerk.closest('li') as HTMLElement;

    const eduBadge = within(clerkRow).getByText('needs Education 1');
    expect(eduBadge.className).toContain('job-req--unmet');

    const clothingBadge = within(clerkRow).getByText('Clothing 2');
    expect(clothingBadge.className).toContain('job-req--met');

    const cook = screen.getByText('Cook');
    const cookRow = cook.closest('li') as HTMLElement;
    const cookEduBadge = within(cookRow).getByText('Education 0');
    expect(cookEduBadge.className).toContain('job-req--met');
  });

  it('shows the current job with a marker', async () => {
    renderPanel(
      playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    );

    const cook = await screen.findByText((content) => content.startsWith('Cook'));
    const cookRow = cook.closest('li') as HTMLElement;
    expect(cookRow.className).toContain('job-listing--current');
    expect(within(cookRow).getByText('Cook (current)')).toBeInTheDocument();
  });

  it('disables Apply (with a title) for unmet education, but not for unmet clothing', async () => {
    renderPanel(
      playerFixture({
        stats: { education: 0, educationProgress: 0, happiness: 50, workExperience: 0 },
        clothing: 0,
      }),
    );

    const clerk = await screen.findByText('Clerk');
    const clerkRow = clerk.closest('li') as HTMLElement;
    const clerkApply = within(clerkRow).getByRole('button', { name: 'Apply' });
    expect(clerkApply).toBeDisabled();
    expect(clerkApply).toHaveAttribute('title', 'Requires education level 1');

    const cook = screen.getByText('Cook');
    const cookRow = cook.closest('li') as HTMLElement;
    const cookApply = within(cookRow).getByRole('button', { name: 'Apply' });
    expect(cookApply).not.toBeDisabled();
  });

  it('applies for a job, gets hired, notifies, and updates the cache', async () => {
    applyForJobMock.mockResolvedValue({
      hired: true,
      reason: null,
      minutesCharged: 240,
      job: 'Cook',
      hourlyWage: 6,
      state: playerFixture({ job: { name: 'Cook', hourlyWage: 6, location: 'Monolith Burgers' } }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderPanel(playerFixture());

    const cook = await screen.findByText('Cook');
    const cookRow = cook.closest('li') as HTMLElement;
    await user.click(within(cookRow).getByRole('button', { name: 'Apply' }));

    expect(applyForJobMock).toHaveBeenCalledWith('alice', 'Cook');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Hired as Cook at R6/h (4h)'));
  });

  it('applies for a job, gets rejected for education, still charges time, and updates the cache', async () => {
    applyForJobMock.mockResolvedValue({
      hired: false,
      reason: 'INSUFFICIENT_EDUCATION',
      minutesCharged: 240,
      job: 'Cook',
      hourlyWage: null,
      state: playerFixture({ timeMinutes: 4080, timeDisplay: '68h' }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    const cook = await screen.findByText('Cook');
    const cookRow = cook.closest('li') as HTMLElement;
    await user.click(within(cookRow).getByRole('button', { name: 'Apply' }));

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith(
        'Application for Cook rejected — more education needed (4h)',
      ),
    );
    expect(queryClient.getQueryData(['player', 'alice'])).toMatchObject({ timeDisplay: '68h' });
  });

  it('renders the problem detail inline on an ApiError and invalidates the player query', async () => {
    applyForJobMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at the Employment Office.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderPanel(playerFixture());

    const cook = await screen.findByText('Cook');
    const cookRow = cook.closest('li') as HTMLElement;
    await user.click(within(cookRow).getByRole('button', { name: 'Apply' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at the Employment Office.');
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['player', 'alice'] });
  });
});
