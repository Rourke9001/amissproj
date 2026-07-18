import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UniversityPanel } from './UniversityPanel';
import { enroll, getCourses, study } from '../../api/university';
import type { CourseDto, EnrollResponse, SaveStateDto, StudyResponse } from '../../api/types';

vi.mock('../../api/university', () => ({ getCourses: vi.fn(), enroll: vi.fn(), study: vi.fn() }));

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
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    location: { id: 'HI_TECH_U', name: 'Hi-Tech U', ringIndex: 6, row: 3, col: 3 },
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

const COURSES: CourseDto[] = [
  {
    id: 1,
    name: 'Junior College',
    status: 'AVAILABLE',
    prereqName: null,
    enrolled: false,
    studiesDone: 0,
  },
  {
    id: 3,
    name: 'Business Administration',
    status: 'LOCKED',
    prereqName: 'Junior College',
    enrolled: false,
    studiesDone: 0,
  },
];

function renderPanel(player: SaveStateDto = playerFixture()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const onNotify = vi.fn();
  render(
    <QueryClientProvider client={queryClient}>
      <UniversityPanel saveId={1} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { queryClient, onNotify };
}

describe('UniversityPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(getCourses).mockResolvedValue(COURSES);
  });

  it('renders each degree with its earned/available/locked status and prereq', async () => {
    renderPanel();
    expect(await screen.findByText('Junior College')).toBeInTheDocument();
    expect(screen.getByText('Available')).toBeInTheDocument();
    expect(screen.getByText('Business Administration')).toBeInTheDocument();
    expect(screen.getByText('Locked')).toBeInTheDocument();
    expect(screen.getByText('Requires: Junior College')).toBeInTheDocument();
  });

  it('enrolls by degree id and applies the returned state', async () => {
    const response: EnrollResponse = {
      feePaid: 50,
      state: playerFixture({ currentCourse: { id: 1, name: 'Junior College', studiesDone: 0 } }),
    };
    vi.mocked(enroll).mockResolvedValue(response);
    const { onNotify, queryClient } = renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Enroll' }));

    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Enrolled, R50 fee paid.'));
    expect(enroll).toHaveBeenCalledWith(1, 1);
    expect(queryClient.getQueryData(['save', 1])).toEqual(response.state);
  });

  it('study takes no degree id and reports graduation', async () => {
    const response: StudyResponse = {
      studiesDone: 10,
      studiesRemaining: 0,
      degreeCompleted: 'Junior College',
      minutesCharged: 360,
      state: playerFixture({ degreesEarned: ['Junior College'] }),
    };
    vi.mocked(study).mockResolvedValue(response);
    const { onNotify } = renderPanel(
      playerFixture({ currentCourse: { id: 1, name: 'Junior College', studiesDone: 9 } }),
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Study' }));

    expect(study).toHaveBeenCalledWith(1);
    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Studied, graduated with a degree in Junior College!'),
    );
  });
});
