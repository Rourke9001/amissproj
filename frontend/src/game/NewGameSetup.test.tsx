import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NewGameSetup } from './NewGameSetup';
import { createSave } from '../api/saves';
import type { SaveSummaryDto } from '../api/types';

vi.mock('../api/saves', () => ({ createSave: vi.fn() }));

function renderNewGameSetup() {
  const onCreated = vi.fn();
  const onCancel = vi.fn();
  // NewGameSetup uses useMutation internally, so it needs a QueryClientProvider
  // ancestor; a fresh QueryClient per test matches this codebase's house style
  // (see SavesPage.test.tsx / WorkAction.test.tsx).
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <NewGameSetup onCreated={onCreated} onCancel={onCancel} />
    </QueryClientProvider>,
  );
  return { onCreated, onCancel };
}

describe('NewGameSetup', () => {
  beforeEach(() => vi.resetAllMocks());

  it('renders four goal sliders defaulting to 50', () => {
    renderNewGameSetup();
    const sliders = screen.getAllByRole('slider');
    expect(sliders).toHaveLength(4);
    sliders.forEach((slider) => expect(slider).toHaveValue('50'));
  });

  it('Randomise sets each slider to a value between 10 and 100', async () => {
    renderNewGameSetup();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Randomise' }));

    for (const slider of screen.getAllByRole('slider')) {
      const value = Number((slider as HTMLInputElement).value);
      expect(value).toBeGreaterThanOrEqual(10);
      expect(value).toBeLessThanOrEqual(100);
    }
  });

  it('Start Game sends the current slider values and calls onCreated with the new save id', async () => {
    const created: SaveSummaryDto = {
      id: 5,
      label: 'Save 1',
      round: 1,
      cash: 100,
      won: false,
      updatedAt: '2026-01-01T00:00:00Z',
    };
    vi.mocked(createSave).mockResolvedValue(created);
    const { onCreated } = renderNewGameSetup();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Start Game' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(5));
    expect(createSave).toHaveBeenCalledWith({
      label: 'Save 1',
      goals: { wealth: 50, happiness: 50, education: 50, career: 50 },
    });
  });

  it('Cancel calls onCancel without creating a save', async () => {
    const { onCancel } = renderNewGameSetup();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onCancel).toHaveBeenCalled();
    expect(createSave).not.toHaveBeenCalled();
  });
});
