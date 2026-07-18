import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BoardScreen } from './BoardScreen';
import { ApiError } from '../api/http';
import { getBoard } from '../api/board';
import { endWeek, getSaveState, move } from '../api/player';
import { getJobs } from '../api/jobs';
import { getCourses } from '../api/university';
import type { BoardDto, SaveStateDto } from '../api/types';

vi.mock('../api/board', () => ({
  getBoard: vi.fn(),
}));

vi.mock('../api/player', () => ({
  getSaveState: vi.fn(),
  move: vi.fn(),
  endWeek: vi.fn(),
}));

// BoardScreen renders the current stop's panel (see game/panels/registry), so
// its api modules must be mocked here even though BoardScreen itself never
// imports them directly.
vi.mock('../api/bank', () => ({
  deposit: vi.fn(),
  withdraw: vi.fn(),
}));

vi.mock('../api/rent', () => ({
  payRent: vi.fn(),
}));

vi.mock('../api/jobs', () => ({
  getJobs: vi.fn(),
  applyForJob: vi.fn(),
  work: vi.fn(),
}));

vi.mock('../api/university', () => ({
  getCourses: vi.fn(),
  enroll: vi.fn(),
  study: vi.fn(),
}));

vi.mock('../api/food', () => ({
  getFoodCatalog: vi.fn(),
  eat: vi.fn(),
  buyGroceries: vi.fn(),
}));

vi.mock('../api/clothes', () => ({
  getClothesCatalog: vi.fn(),
  buyClothes: vi.fn(),
}));

const getBoardMock = vi.mocked(getBoard);
const getSaveStateMock = vi.mocked(getSaveState);
const moveMock = vi.mocked(move);
const endWeekMock = vi.mocked(endWeek);
const getJobsMock = vi.mocked(getJobs);
const getCoursesMock = vi.mocked(getCourses);

// Mirrors amiss-core's Board.CELLS clockwise ring layout.
const BOARD_FIXTURE: BoardDto = {
  stops: [
    { id: 'LOW_COST_HOUSING', name: 'Low-Cost Housing', ringIndex: 0, row: 0, col: 2 },
    { id: 'PAWN_SHOP', name: 'Pawn Shop', ringIndex: 1, row: 0, col: 3 },
    { id: 'Z_MART', name: 'Z-Mart', ringIndex: 2, row: 0, col: 4 },
    { id: 'MONOLITH_BURGERS', name: 'Monolith Burgers', ringIndex: 3, row: 1, col: 4 },
    { id: 'QT_CLOTHING', name: 'QT Clothing', ringIndex: 4, row: 2, col: 4 },
    { id: 'SOCKET_CITY', name: 'Socket City', ringIndex: 5, row: 3, col: 4 },
    { id: 'HI_TECH_U', name: 'Hi-Tech U', ringIndex: 6, row: 3, col: 3 },
    { id: 'EMPLOYMENT_OFFICE', name: 'Employment Office', ringIndex: 7, row: 3, col: 1 },
    { id: 'FACTORY', name: 'Factory', ringIndex: 8, row: 3, col: 0 },
    { id: 'BANK', name: 'Bank', ringIndex: 9, row: 2, col: 0 },
    { id: 'BLACKS_MARKET', name: "Black's Market", ringIndex: 10, row: 1, col: 0 },
    { id: 'LE_SECURITY_APARTMENTS', name: 'Le Security Apartments', ringIndex: 11, row: 0, col: 0 },
    { id: 'RENT_OFFICE', name: 'Rent Office', ringIndex: 12, row: 0, col: 1 },
  ],
  travel: { minutesPerStep: 40, enterBuildingMinutes: 120 },
};

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
    location: BOARD_FIXTURE.stops[9], // BANK
    ...overrides,
  };
}

function renderBoardScreen() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <BoardScreen saveId={42} />
    </QueryClientProvider>,
  );
}

describe('BoardScreen', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getBoardMock.mockResolvedValue(BOARD_FIXTURE);
    getSaveStateMock.mockResolvedValue(playerFixture());
    getJobsMock.mockResolvedValue([{ id: 1, name: 'Bank Janitor', location: 'Bank', wage: 6 }]);
    getCoursesMock.mockResolvedValue([
      {
        id: 1,
        name: 'Certificate',
        status: 'AVAILABLE',
        prereqName: null,
        enrolled: false,
        studiesDone: 0,
      },
    ]);
  });

  it('renders all 13 hotspots positioned from the board data', async () => {
    renderBoardScreen();

    const lowCostHousing = await screen.findByRole('button', { name: 'Low-Cost Housing' });
    expect(lowCostHousing.style.left).toBe('40%');
    expect(lowCostHousing.style.top).toBe('0%');

    const bank = screen.getByRole('button', { name: 'Bank' });
    expect(bank.style.left).toBe('0%');
    expect(bank.style.top).toBe('50%');
    expect(bank.className).toContain('stop-hotspot--current');

    expect(document.querySelectorAll('.stop-hotspot')).toHaveLength(13);
  });

  it('positions the player token at the current stop', async () => {
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Bank' });

    const token = document.querySelector('.player-token') as HTMLElement;
    expect(token.style.left).toBe('10%');
    expect(token.style.top).toBe('62.5%');
  });

  it('shows the clock overlay with the current time and round', async () => {
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Bank' });

    const clock = document.querySelector('.board-clock');
    expect(clock?.textContent).toContain('72h');
    expect(clock?.textContent).toContain('Round 3');
  });

  it('shows the travel cost tooltip on a non-current hotspot', async () => {
    renderBoardScreen();

    const lowCostHousing = await screen.findByRole('button', { name: 'Low-Cost Housing' });
    expect(within(lowCostHousing).getByText('4 stops — 4h 40m')).toBeInTheDocument();
  });

  it('shows the Enter tooltip on the current hotspot', async () => {
    renderBoardScreen();

    const bank = await screen.findByRole('button', { name: 'Bank' });
    expect(within(bank).getByText('Enter — 2h')).toBeInTheDocument();
  });

  it('clicking a non-current stop immediately calls move (no confirm step)', async () => {
    moveMock.mockResolvedValue({
      target: 'LOW_COST_HOUSING',
      steps: 4,
      minutesCharged: 280,
      state: playerFixture({
        location: BOARD_FIXTURE.stops[0],
        timeMinutes: 4040,
        timeDisplay: '67h 20m',
      }),
    });
    const user = userEvent.setup();
    renderBoardScreen();

    await user.click(await screen.findByRole('button', { name: 'Low-Cost Housing' }));

    expect(moveMock).toHaveBeenCalledWith(42, 'LOW_COST_HOUSING');
    expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument();

    const feed = screen.getByRole('log', { name: 'Notifications' });
    expect(await within(feed).findByText('Moved to Low-Cost Housing (4h 40m)')).toBeInTheDocument();

    const clock = document.querySelector('.board-clock');
    expect(clock?.textContent).toContain('67h 20m');
  });

  it('shows the problem detail in the feed on a move error', async () => {
    moveMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:insufficient-time',
        title: 'Conflict',
        status: 409,
        detail: 'Not enough time left this week.',
      }),
    );
    const user = userEvent.setup();
    renderBoardScreen();

    await user.click(await screen.findByRole('button', { name: 'Low-Cost Housing' }));

    const feed = screen.getByRole('log', { name: 'Notifications' });
    expect(await within(feed).findByText('Not enough time left this week.')).toBeInTheDocument();
  });

  it('refetches the player state when a move is rejected (an exact-zero landing is charged server-side)', async () => {
    getSaveStateMock
      .mockResolvedValueOnce(playerFixture({ timeMinutes: 120, timeDisplay: '2h' }))
      .mockResolvedValue(playerFixture({ timeMinutes: 0, timeDisplay: '0h', weekOver: true }));
    moveMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:week-over',
        title: 'Conflict',
        status: 409,
        detail: 'The week is used up; end it via POST .../end-week',
      }),
    );
    const user = userEvent.setup();
    renderBoardScreen();

    await user.click(await screen.findByRole('button', { name: 'Bank' }));

    // The 409 lands in the feed AND the state is refetched, so the charged clock
    // and the End Week button appear without a manual reload.
    expect(await screen.findByRole('button', { name: 'End Week' })).toBeInTheDocument();
    expect(getSaveStateMock.mock.calls.length).toBeGreaterThanOrEqual(2);
    const clock = document.querySelector('.board-clock');
    expect(clock?.textContent).toContain('0h');
  });

  it('shows a fallback message in the feed on a non-ApiError move failure', async () => {
    moveMock.mockRejectedValue(new Error('network down'));
    const user = userEvent.setup();
    renderBoardScreen();

    await user.click(await screen.findByRole('button', { name: 'Low-Cost Housing' }));

    const feed = screen.getByRole('log', { name: 'Notifications' });
    expect(await within(feed).findByText('Could not reach the server.')).toBeInTheDocument();
  });

  it('shows HUD stats sourced from the player state', async () => {
    getSaveStateMock.mockResolvedValue(
      playerFixture({ job: { name: 'Cashier', hourlyWage: 25, location: 'Z_MART' } }),
    );
    renderBoardScreen();

    await screen.findByText('Bank', { selector: '.hud-location-name' });

    expect(screen.getByText('R500')).toBeInTheDocument();
    expect(screen.getByText('R100')).toBeInTheDocument();
    expect(screen.getByText('Cashier R25/h')).toBeInTheDocument();
    expect(screen.getByText('2 wk')).toBeInTheDocument();
    expect(screen.getByText('50')).toBeInTheDocument();
    expect(screen.getByText('500 / 5000')).toBeInTheDocument();
  });

  it('renders the Bank panel when standing at the Bank stop', async () => {
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Bank' });

    const centre = document.querySelector('.board-centre') as HTMLElement;
    expect(within(centre).getByRole('heading', { name: 'Bank' })).toBeInTheDocument();
    expect(within(centre).getByLabelText('Amount')).toBeInTheDocument();
  });

  it('renders the Employment Office panel when standing at that stop', async () => {
    getSaveStateMock.mockResolvedValue(playerFixture({ location: BOARD_FIXTURE.stops[7] })); // EMPLOYMENT_OFFICE
    const user = userEvent.setup();
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Employment Office' });

    const centre = document.querySelector('.board-centre') as HTMLElement;
    expect(
      await within(centre).findByRole('heading', { name: 'Employment Office' }),
    ).toBeInTheDocument();

    await user.click(within(centre).getByRole('button', { name: 'Bank' }));
    expect(within(centre).getByText('Bank Janitor')).toBeInTheDocument();
  });

  it('renders the Hi-Tech U panel when standing at that stop', async () => {
    getSaveStateMock.mockResolvedValue(playerFixture({ location: BOARD_FIXTURE.stops[6] })); // HI_TECH_U
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Hi-Tech U' });

    const centre = document.querySelector('.board-centre') as HTMLElement;
    expect(await within(centre).findByRole('heading', { name: 'Hi-Tech U' })).toBeInTheDocument();
    expect(within(centre).getByRole('button', { name: 'Enroll' })).toBeInTheDocument();
  });

  it('shows the WorkAction button when the player has a job located at the current stop', async () => {
    getSaveStateMock.mockResolvedValue(
      playerFixture({ job: { name: 'Bank Janitor', hourlyWage: 6, location: 'Bank' } }),
    );
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Bank' });

    const centre = document.querySelector('.board-centre') as HTMLElement;
    expect(within(centre).getByRole('button', { name: 'Work a shift (6h)' })).toBeInTheDocument();
  });

  it('renders the Home panel when standing at Low-Cost Housing', async () => {
    getSaveStateMock.mockResolvedValue(playerFixture({ location: BOARD_FIXTURE.stops[0] })); // LOW_COST_HOUSING
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Low-Cost Housing' });

    const centre = document.querySelector('.board-centre') as HTMLElement;
    expect(await within(centre).findByRole('heading', { name: 'Home' })).toBeInTheDocument();
  });

  it('renders the DefaultPanel for a stop with no registered panel', async () => {
    getSaveStateMock.mockResolvedValue(playerFixture({ location: BOARD_FIXTURE.stops[1] })); // PAWN_SHOP
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Pawn Shop' });

    const centre = document.querySelector('.board-centre') as HTMLElement;
    expect(
      within(centre).getByText('Pawn Shop opens with the KAN-5 economy epic.'),
    ).toBeInTheDocument();
  });

  it('does not show the End Week button while the week is still running', async () => {
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Bank' });

    expect(screen.queryByRole('button', { name: 'End Week' })).not.toBeInTheDocument();
  });

  it('runs the End Week flow: button click, modal summary, close, and cache update', async () => {
    getSaveStateMock.mockResolvedValue(playerFixture({ weekOver: true, round: 3 }));
    endWeekMock.mockResolvedValue({
      round: 4,
      fed: false,
      rentDue: true,
      debtCharged: true,
      won: false,
      economy: {
        event: 'NONE',
        severity: null,
        fired: false,
        wageCutTo: null,
        bankWiped: false,
        happinessLost: 0,
      },
      doctorVisit: { triggered: false, hoursLost: 0, happinessLost: 0, cashLost: 0 },
      state: playerFixture({ weekOver: false, round: 4, timeDisplay: '72h' }),
    });
    const user = userEvent.setup();
    renderBoardScreen();

    const endWeekButton = await screen.findByRole('button', { name: 'End Week' });
    await user.click(endWeekButton);

    expect(endWeekMock).toHaveBeenCalledWith(42);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Week over — Round 4 begins')).toBeInTheDocument();
    expect(
      within(dialog).getByText('You went hungry — the coming week is shorter.'),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText('Unpaid rent was charged to your debt (+R80).'),
    ).toBeInTheDocument();
    expect(within(dialog).getByText('Rent is due this round.')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    const clock = document.querySelector('.board-clock');
    expect(clock?.textContent).toContain('Round 4');
  });

  it('shows the problem detail in the feed on an end-week error (week not over)', async () => {
    getSaveStateMock.mockResolvedValue(playerFixture({ weekOver: true }));
    endWeekMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:week-not-over',
        title: 'Conflict',
        status: 409,
        detail: 'The week is not over yet.',
      }),
    );
    const user = userEvent.setup();
    renderBoardScreen();

    await user.click(await screen.findByRole('button', { name: 'End Week' }));

    const feed = screen.getByRole('log', { name: 'Notifications' });
    expect(await within(feed).findByText('The week is not over yet.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows the win banner once the save has won, and dismisses it without leaving the screen', async () => {
    getSaveStateMock.mockResolvedValue(playerFixture({ won: true }));
    renderBoardScreen();
    const user = userEvent.setup();

    expect(await screen.findByRole('dialog', { name: 'You won' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Keep Playing' }));

    expect(screen.queryByRole('dialog', { name: 'You won' })).not.toBeInTheDocument();
  });
});
