import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { SavesPage } from './SavesPage';
import { deleteSave, listSaves } from '../api/saves';
import type { SaveSummaryDto } from '../api/types';

vi.mock('../api/saves', () => ({ listSaves: vi.fn(), createSave: vi.fn(), deleteSave: vi.fn() }));

const SAVES: SaveSummaryDto[] = [
  { id: 1, label: 'Save 1', round: 3, cash: 250, won: false, updatedAt: '2026-01-01T00:00:00Z' },
  { id: 2, label: 'Winner', round: 8, cash: 5000, won: true, updatedAt: '2026-01-02T00:00:00Z' },
];

function renderSavesPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SavesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { queryClient };
}

describe('SavesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listSaves).mockResolvedValue(SAVES);
  });

  it('lists every save with its round, cash, and won status', async () => {
    renderSavesPage();
    expect(await screen.findByText('Save 1')).toBeInTheDocument();
    expect(screen.getByText('Winner')).toBeInTheDocument();
    expect(screen.getByText(/Won!/)).toBeInTheDocument();
  });

  it('shows an empty-state message when there are no saves', async () => {
    vi.mocked(listSaves).mockResolvedValue([]);
    renderSavesPage();
    expect(await screen.findByText('No saves yet, start a new game.')).toBeInTheDocument();
  });

  it('deletes a save after confirmation and refetches the list', async () => {
    vi.mocked(deleteSave).mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderSavesPage();
    const user = userEvent.setup();

    const deleteButtons = await screen.findAllByRole('button', { name: 'Delete' });
    await user.click(deleteButtons[0]);

    await waitFor(() => expect(deleteSave).toHaveBeenCalledWith(1));
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderSavesPage();
    const user = userEvent.setup();

    const deleteButtons = await screen.findAllByRole('button', { name: 'Delete' });
    await user.click(deleteButtons[0]);

    expect(deleteSave).not.toHaveBeenCalled();
  });

  it('New Game shows the goal-setup flow instead of the list', async () => {
    renderSavesPage();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'New Game' }));

    expect(screen.getByRole('heading', { name: 'New Game' })).toBeInTheDocument();
    expect(screen.queryByText('Save 1')).not.toBeInTheDocument();
  });
});
