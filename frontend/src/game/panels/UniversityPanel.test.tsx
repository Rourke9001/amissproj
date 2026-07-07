import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UniversityPanel } from './UniversityPanel';
import { enroll, getCourses, study } from '../../api/university';
import { getJobs } from '../../api/jobs';
import { ApiError } from '../../api/http';
import type { CoursesDto, JobListingDto, PlayerStateDto } from '../../api/types';

vi.mock('../../api/university', () => ({
  getCourses: vi.fn(),
  enroll: vi.fn(),
  study: vi.fn(),
}));

vi.mock('../../api/jobs', () => ({
  getJobs: vi.fn(),
  applyForJob: vi.fn(),
  work: vi.fn(),
}));

const getCoursesMock = vi.mocked(getCourses);
const enrollMock = vi.mocked(enroll);
const studyMock = vi.mocked(study);
const getJobsMock = vi.mocked(getJobs);

const COURSES_FIXTURE: CoursesDto = {
  degrees: [
    { level: 1, name: 'Certificate' },
    { level: 2, name: 'Associate' },
    { level: 3, name: 'Bachelor' },
    { level: 4, name: 'Master' },
    { level: 5, name: 'Doctorate' },
    { level: 6, name: 'Fellowship' },
    { level: 7, name: 'Chair' },
    { level: 8, name: 'Laureate' },
  ],
  enrollFee: 100,
  studiesPerDegree: 10,
  studyMinutes: 240,
};

const JOBS_FIXTURE: JobListingDto[] = [
  {
    name: 'Clerk',
    requiredEducation: 1,
    hourlyWage: 10,
    location: 'Socket City',
    requiredClothing: 2,
  },
  {
    name: 'SalesPerson',
    requiredEducation: 1,
    hourlyWage: 12,
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
    location: { id: 'HI_TECH_U', name: 'Hi-Tech U', ringIndex: 6, row: 3, col: 3 },
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
      <UniversityPanel username="alice" player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('UniversityPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCoursesMock.mockResolvedValue(COURSES_FIXTURE);
    getJobsMock.mockResolvedValue(JOBS_FIXTURE);
  });

  it('renders the degree list with completed and in-progress markers', async () => {
    renderPanel(
      playerFixture({
        stats: { education: 1, educationProgress: 0, happiness: 50, workExperience: 0 },
      }),
    );

    const level1 = await screen.findByText('Level 1: Certificate (completed)');
    expect(level1.closest('li')?.className).toContain('degree--completed');

    const level2 = screen.getByText('Level 2: Associate (in progress)');
    expect(level2.closest('li')?.className).toContain('degree--next');

    const level3 = screen.getByText('Level 3: Bachelor');
    expect(level3.closest('li')?.className).not.toContain('degree--completed');
    expect(level3.closest('li')?.className).not.toContain('degree--next');
  });

  it('lists the jobs a level unlocks and omits the line when none', async () => {
    renderPanel(playerFixture());

    const level1 = await screen.findByText((c) => c.startsWith('Level 1:'));
    expect(
      within(level1.closest('li') as HTMLElement).getByText('Unlocks: Clerk, SalesPerson'),
    ).toBeInTheDocument();

    const level2 = screen.getByText((c) => c.startsWith('Level 2:'));
    expect(
      within(level2.closest('li') as HTMLElement).queryByText(/Unlocks:/),
    ).not.toBeInTheDocument();
  });

  it('shows an Enroll button when not enrolled', async () => {
    renderPanel(playerFixture());

    expect(await screen.findByRole('button', { name: 'Enroll (R100)' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Study/ })).not.toBeInTheDocument();
  });

  it('shows the study progress and a Study button when enrolled', async () => {
    renderPanel(
      playerFixture({
        stats: { education: 0, educationProgress: 3, happiness: 50, workExperience: 0 },
      }),
    );

    expect(await screen.findByText('Studies: 3/10')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Study (4h)' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Enroll/ })).not.toBeInTheDocument();
  });

  it('shows neither button at education level 8', async () => {
    renderPanel(
      playerFixture({
        stats: { education: 8, educationProgress: 0, happiness: 50, workExperience: 0 },
      }),
    );

    expect(await screen.findByText('All degrees completed.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Enroll/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Study/ })).not.toBeInTheDocument();
  });

  it('enrolls successfully, updates the cache, and notifies', async () => {
    enrollMock.mockResolvedValue({
      feePaid: 100,
      state: playerFixture({
        cash: 400,
        stats: { education: 0, educationProgress: 1, happiness: 50, workExperience: 0 },
      }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderPanel(playerFixture());

    await user.click(await screen.findByRole('button', { name: 'Enroll (R100)' }));

    expect(enrollMock).toHaveBeenCalledWith('alice');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Enrolled at Hi-Tech U (R100)'));
  });

  it('studies successfully, updates the cache, and notifies with progress', async () => {
    studyMock.mockResolvedValue({
      progress: 4,
      studiesRemaining: 6,
      degreeCompleted: null,
      educationLevel: 0,
      minutesCharged: 240,
      state: playerFixture({
        stats: { education: 0, educationProgress: 4, happiness: 50, workExperience: 0 },
      }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderPanel(
      playerFixture({
        stats: { education: 0, educationProgress: 3, happiness: 50, workExperience: 0 },
      }),
    );

    await user.click(await screen.findByRole('button', { name: 'Study (4h)' }));

    expect(studyMock).toHaveBeenCalledWith('alice');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Studied — 4/10 (4h)'));
  });

  it('notifies with the degree-completed message when a level finishes', async () => {
    studyMock.mockResolvedValue({
      progress: 10,
      studiesRemaining: 0,
      degreeCompleted: 'Certificate',
      educationLevel: 1,
      minutesCharged: 240,
      state: playerFixture({
        stats: { education: 1, educationProgress: 0, happiness: 50, workExperience: 0 },
      }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderPanel(
      playerFixture({
        stats: { education: 0, educationProgress: 9, happiness: 50, workExperience: 0 },
      }),
    );

    await user.click(await screen.findByRole('button', { name: 'Study (4h)' }));

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Degree completed: Certificate — education level 1!'),
    );
  });

  it('renders the problem detail inline on an ApiError and invalidates the player query', async () => {
    enrollMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:insufficient-funds',
        title: 'Conflict',
        status: 409,
        detail: 'Not enough cash for the enrollment fee.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderPanel(playerFixture());

    await user.click(await screen.findByRole('button', { name: 'Enroll (R100)' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Not enough cash for the enrollment fee.',
    );
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['player', 'alice'] });
  });
});
