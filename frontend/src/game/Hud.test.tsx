import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Hud } from './Hud';
import type { PlayerStateDto } from '../api/types';

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
    location: { id: 'BANK', name: 'Bank', ringIndex: 9, row: 2, col: 0 },
    ...overrides,
  };
}

describe('Hud', () => {
  it('renders cash, bank, food, happiness and goal progress from the player state', () => {
    render(<Hud player={playerFixture()} onEndWeek={vi.fn()} endWeekPending={false} />);

    expect(screen.getByText('Bank', { selector: '.hud-location-name' })).toBeInTheDocument();
    expect(screen.getByText('R500')).toBeInTheDocument();
    expect(screen.getByText('R100')).toBeInTheDocument();
    expect(screen.getByText('2 wk')).toBeInTheDocument();
    expect(screen.getByText('50')).toBeInTheDocument();
    expect(screen.getByText('500 / 5000')).toBeInTheDocument();
    expect(screen.getByText('50 / 100')).toBeInTheDocument();
    expect(screen.getByText('0 / 10')).toBeInTheDocument();
    expect(screen.getByText('0 / 100')).toBeInTheDocument();
  });

  it('shows Unemployed when there is no job', () => {
    render(
      <Hud player={playerFixture({ job: null })} onEndWeek={vi.fn()} endWeekPending={false} />,
    );
    expect(screen.getByText('Unemployed')).toBeInTheDocument();
  });

  it('shows the job name and hourly wage when employed', () => {
    render(
      <Hud
        player={playerFixture({ job: { name: 'Cashier', hourlyWage: 25, location: 'Z_MART' } })}
        onEndWeek={vi.fn()}
        endWeekPending={false}
      />,
    );
    expect(screen.getByText('Cashier R25/h')).toBeInTheDocument();
  });

  it('hides the debt row when debt is 0', () => {
    render(<Hud player={playerFixture({ debt: 0 })} onEndWeek={vi.fn()} endWeekPending={false} />);
    expect(screen.queryByText('Debt')).not.toBeInTheDocument();
  });

  it('shows the debt row when debt is greater than 0', () => {
    render(
      <Hud player={playerFixture({ debt: 250 })} onEndWeek={vi.fn()} endWeekPending={false} />,
    );
    expect(screen.getByText('Debt')).toBeInTheDocument();
    expect(screen.getByText('R250')).toBeInTheDocument();
  });

  it('shows the rent-due warning banner only when rentDue is true', () => {
    const { rerender } = render(
      <Hud player={playerFixture({ rentDue: false })} onEndWeek={vi.fn()} endWeekPending={false} />,
    );
    expect(screen.queryByText(/Rent is due this round/)).not.toBeInTheDocument();

    rerender(
      <Hud player={playerFixture({ rentDue: true })} onEndWeek={vi.fn()} endWeekPending={false} />,
    );
    expect(screen.getByText('Rent is due this round — visit the Rent Office.')).toBeInTheDocument();
  });

  it('shows no End Week button when the week is not over', () => {
    render(
      <Hud
        player={playerFixture({ weekOver: false })}
        onEndWeek={vi.fn()}
        endWeekPending={false}
      />,
    );
    expect(screen.queryByRole('button', { name: 'End Week' })).not.toBeInTheDocument();
  });

  it('shows an End Week button that calls onEndWeek when the week is over', async () => {
    const user = userEvent.setup();
    const onEndWeek = vi.fn();
    render(
      <Hud
        player={playerFixture({ weekOver: true })}
        onEndWeek={onEndWeek}
        endWeekPending={false}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'End Week' }));
    expect(onEndWeek).toHaveBeenCalled();
  });
});
