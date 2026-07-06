import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BoardScreen } from './BoardScreen';
import { ApiError } from '../api/http';
import { getBoard } from '../api/board';
import { getPlayerState, move } from '../api/player';
import type { BoardDto, PlayerStateDto } from '../api/types';

vi.mock('../api/board', () => ({
  getBoard: vi.fn(),
}));

vi.mock('../api/player', () => ({
  getPlayerState: vi.fn(),
  move: vi.fn(),
}));

const getBoardMock = vi.mocked(getBoard);
const getPlayerStateMock = vi.mocked(getPlayerState);
const moveMock = vi.mocked(move);

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
      <BoardScreen username="alice" />
    </QueryClientProvider>,
  );
}

describe('BoardScreen', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getBoardMock.mockResolvedValue(BOARD_FIXTURE);
    getPlayerStateMock.mockResolvedValue(playerFixture());
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

  it('shows the status line with the current round, time and cash', async () => {
    renderBoardScreen();
    await screen.findByRole('button', { name: 'Bank' });

    const status = document.querySelector('.board-status');
    expect(status?.textContent).toContain('72h');
    expect(status?.textContent).toContain('R500');
    expect(status?.textContent).toContain('3');
  });

  it('clicking another stop shows the confirm panel with steps and cost', async () => {
    const user = userEvent.setup();
    renderBoardScreen();

    await user.click(await screen.findByRole('button', { name: 'Low-Cost Housing' }));

    expect(
      await screen.findByText('Travel to Low-Cost Housing: 4 stops, costs 4h 40m'),
    ).toBeInTheDocument();
  });

  it('clicking the current stop shows the Enter wording with the enter-only cost', async () => {
    const user = userEvent.setup();
    renderBoardScreen();

    await user.click(await screen.findByRole('button', { name: 'Bank' }));

    expect(await screen.findByText('Enter Bank: costs 2h')).toBeInTheDocument();
  });

  it('confirming calls move() with the target id and updates state from the response', async () => {
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
    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(moveMock).toHaveBeenCalledWith('alice', 'LOW_COST_HOUSING');
    expect(await screen.findByText('Moved to Low-Cost Housing (4h 40m)')).toBeInTheDocument();
    const status = document.querySelector('.board-status');
    expect(status?.textContent).toContain('67h 20m');
  });

  it('shows the problem detail as the notice on a 409 insufficient-time from move()', async () => {
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
    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(await screen.findByText('Not enough time left this week.')).toBeInTheDocument();
  });
});
