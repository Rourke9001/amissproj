import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WinBanner } from './WinBanner';

describe('WinBanner', () => {
  it('renders a win message and calls onDismiss when closed', async () => {
    const onDismiss = vi.fn();
    render(<WinBanner onDismiss={onDismiss} />);
    const user = userEvent.setup();

    expect(screen.getByRole('dialog', { name: 'You won' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Keep Playing' }));

    expect(onDismiss).toHaveBeenCalled();
  });
});
