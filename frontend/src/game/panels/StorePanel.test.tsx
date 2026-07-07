import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';

const ROWS: StoreRow[] = [
  { id: 'BURGER', label: 'Burger', price: 32, actionLabel: 'Eat' },
  { id: 'PIZZA', label: 'Pizza', price: 45, detail: '+2 wk', actionLabel: 'Buy' },
];

describe('StorePanel', () => {
  it('renders the heading, status line, rows, and prices', () => {
    render(
      <StorePanel
        heading="Monolith Burgers"
        statusLine="You have food stored for the coming week."
        rows={ROWS}
        onAction={vi.fn()}
        pending={false}
        error={null}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Monolith Burgers' })).toBeInTheDocument();
    expect(screen.getByText('You have food stored for the coming week.')).toBeInTheDocument();

    const burgerRow = screen.getByText('Burger').closest('li') as HTMLElement;
    expect(within(burgerRow).getByText('R32')).toBeInTheDocument();
    expect(within(burgerRow).getByRole('button', { name: 'Eat' })).toBeInTheDocument();

    const pizzaRow = screen.getByText('Pizza').closest('li') as HTMLElement;
    expect(within(pizzaRow).getByText('R45')).toBeInTheDocument();
    expect(within(pizzaRow).getByText('+2 wk')).toBeInTheDocument();
    expect(within(pizzaRow).getByRole('button', { name: 'Buy' })).toBeInTheDocument();
  });

  it('omits the status line when none is given', () => {
    render(
      <StorePanel
        heading="Black's Market"
        rows={ROWS}
        onAction={vi.fn()}
        pending={false}
        error={null}
      />,
    );

    expect(screen.queryByText(/food stored/)).not.toBeInTheDocument();
  });

  it('fires onAction with the row id when its button is clicked', async () => {
    const onAction = vi.fn();
    const user = userEvent.setup();
    render(
      <StorePanel
        heading="Monolith Burgers"
        rows={ROWS}
        onAction={onAction}
        pending={false}
        error={null}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Eat' }));

    expect(onAction).toHaveBeenCalledWith('BURGER');
  });

  it('disables every action button while pending', () => {
    render(
      <StorePanel
        heading="Monolith Burgers"
        rows={ROWS}
        onAction={vi.fn()}
        pending={true}
        error={null}
      />,
    );

    expect(screen.getByRole('button', { name: 'Eat' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Buy' })).toBeDisabled();
  });

  it('disables a row explicitly marked disabled even when not pending', () => {
    render(
      <StorePanel
        heading="QT Clothing"
        rows={[
          { id: 'CASUAL', label: 'Casual Clothes', price: 20, actionLabel: 'Buy', disabled: true },
        ]}
        onAction={vi.fn()}
        pending={false}
        error={null}
      />,
    );

    expect(screen.getByRole('button', { name: 'Buy' })).toBeDisabled();
  });

  it('renders the error paragraph when error is non-null', () => {
    render(
      <StorePanel
        heading="Monolith Burgers"
        rows={ROWS}
        onAction={vi.fn()}
        pending={false}
        error="Must be at Monolith Burgers."
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Must be at Monolith Burgers.');
  });

  it('renders no error paragraph when error is null', () => {
    render(
      <StorePanel
        heading="Monolith Burgers"
        rows={ROWS}
        onAction={vi.fn()}
        pending={false}
        error={null}
      />,
    );

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
