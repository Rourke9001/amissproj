import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BankPanel } from './BankPanel';
import { deposit, withdraw } from '../../api/bank';
import { ApiError } from '../../api/http';
import type { SaveStateDto } from '../../api/types';

vi.mock('../../api/bank', () => ({
  deposit: vi.fn(),
  withdraw: vi.fn(),
}));

const depositMock = vi.mocked(deposit);
const withdrawMock = vi.mocked(withdraw);

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
    location: { id: 'BANK', name: 'Bank', ringIndex: 9, row: 2, col: 0 },
    ...overrides,
  };
}

function renderBankPanel(player: SaveStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <BankPanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('BankPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the bank balance heading', () => {
    renderBankPanel(playerFixture({ bank: 250 }));
    expect(screen.getByRole('heading', { name: 'Bank' })).toBeInTheDocument();
    expect(screen.getByText('Bank: R250')).toBeInTheDocument();
  });

  it.each([
    ['abc', 'Enter a positive whole amount.'],
    ['0', 'Enter a positive whole amount.'],
    ['-5', 'Enter a positive whole amount.'],
    ['1.5', 'Enter a positive whole amount.'],
  ])(
    'rejects a non-positive-whole amount %s on deposit without calling the api',
    async (raw, message) => {
      const user = userEvent.setup();
      renderBankPanel(playerFixture());

      await user.type(screen.getByLabelText('Amount'), raw);
      await user.click(screen.getByRole('button', { name: 'Deposit' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(message);
      expect(depositMock).not.toHaveBeenCalled();
      expect(withdrawMock).not.toHaveBeenCalled();
    },
  );

  it('rejects a deposit greater than cash on hand without calling the api', async () => {
    const user = userEvent.setup();
    renderBankPanel(playerFixture({ cash: 100 }));

    await user.type(screen.getByLabelText('Amount'), '150');
    await user.click(screen.getByRole('button', { name: 'Deposit' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('You only have R100 in cash.');
    expect(depositMock).not.toHaveBeenCalled();
  });

  it('rejects a withdrawal greater than the bank balance without calling the api', async () => {
    const user = userEvent.setup();
    renderBankPanel(playerFixture({ bank: 100 }));

    await user.type(screen.getByLabelText('Amount'), '150');
    await user.click(screen.getByRole('button', { name: 'Withdraw' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('You only have R100 in the bank.');
    expect(withdrawMock).not.toHaveBeenCalled();
  });

  it('deposits successfully, calls the api with a numeric amount, notifies, and clears the input', async () => {
    depositMock.mockResolvedValue({
      operation: 'deposit',
      amount: 50,
      state: playerFixture({ cash: 450, bank: 150 }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderBankPanel(playerFixture());

    const input = screen.getByLabelText('Amount') as HTMLInputElement;
    await user.type(input, '50');
    await user.click(screen.getByRole('button', { name: 'Deposit' }));

    expect(depositMock).toHaveBeenCalledWith(42, 50);
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Deposited R50'));
    expect(input.value).toBe('');
  });

  it('withdraws successfully, calls the api with a numeric amount, and notifies', async () => {
    withdrawMock.mockResolvedValue({
      operation: 'withdraw',
      amount: 30,
      state: playerFixture({ cash: 530, bank: 70 }),
    });
    const user = userEvent.setup();
    const { onNotify } = renderBankPanel(playerFixture());

    const input = screen.getByLabelText('Amount') as HTMLInputElement;
    await user.type(input, '30');
    await user.click(screen.getByRole('button', { name: 'Withdraw' }));

    expect(withdrawMock).toHaveBeenCalledWith(42, 30);
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Withdrew R30'));
    expect(input.value).toBe('');
  });

  it('renders the problem detail inline on a 409 insufficient-funds error and does not notify', async () => {
    depositMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:insufficient-funds',
        title: 'Conflict',
        status: 409,
        detail: 'Not enough cash on hand.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify } = renderBankPanel(playerFixture());

    await user.type(screen.getByLabelText('Amount'), '50');
    await user.click(screen.getByRole('button', { name: 'Deposit' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Not enough cash on hand.');
    expect(onNotify).not.toHaveBeenCalled();
  });

  it('invalidates the player query on a transaction error so no stale state lingers', async () => {
    depositMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:insufficient-funds',
        title: 'Conflict',
        status: 409,
        detail: 'Not enough cash on hand.',
      }),
    );
    const user = userEvent.setup();
    const { invalidateSpy } = renderBankPanel(playerFixture());

    await user.type(screen.getByLabelText('Amount'), '50');
    await user.click(screen.getByRole('button', { name: 'Deposit' }));

    await screen.findByRole('alert');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
