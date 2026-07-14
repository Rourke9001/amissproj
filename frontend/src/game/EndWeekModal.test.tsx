import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EndWeekModal } from './EndWeekModal';
import type { SaveStateDto, EconomyEventDto } from '../api/types';

function stateFixture(): SaveStateDto {
  return {
    id: 42,
    label: 'Save 42',
    round: 4,
    timeMinutes: 4320,
    timeDisplay: '72h',
    weekOver: false,
    cash: 500,
    bank: 100,
    debt: 0,
    rentDue: true,
    foodWeeks: 1,
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
  };
}

const noEvent: EconomyEventDto = {
  event: 'NONE',
  severity: null,
  fired: false,
  wageCutTo: null,
  bankWiped: false,
  happinessLost: 0,
};

describe('EndWeekModal', () => {
  it('shows the round, fed, debtCharged and rentDue summary lines', () => {
    render(
      <EndWeekModal
        result={{
          round: 4,
          fed: true,
          rentDue: true,
          debtCharged: true,
          won: false,
          economy: noEvent,
          state: stateFixture(),
        }}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Week over — Round 4 begins')).toBeInTheDocument();
    expect(screen.getByText('You ate this week.')).toBeInTheDocument();
    expect(screen.getByText('Unpaid rent was charged to your debt (+R80).')).toBeInTheDocument();
    expect(screen.getByText('Rent is due this round.')).toBeInTheDocument();
  });

  it('shows the hungry message and hides debtCharged/rentDue lines when false', () => {
    render(
      <EndWeekModal
        result={{
          round: 5,
          fed: false,
          rentDue: false,
          debtCharged: false,
          won: false,
          economy: noEvent,
          state: stateFixture(),
        }}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('You went hungry — the coming week is shorter.')).toBeInTheDocument();
    expect(screen.queryByText(/charged to your debt/)).not.toBeInTheDocument();
    expect(screen.queryByText('Rent is due this round.')).not.toBeInTheDocument();
  });

  it('calls onClose when the Close button is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <EndWeekModal
        result={{
          round: 4,
          fed: true,
          rentDue: false,
          debtCharged: false,
          won: false,
          economy: noEvent,
          state: stateFixture(),
        }}
        onClose={onClose}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('reports a boom', () => {
    render(
      <EndWeekModal
        result={{
          round: 4,
          fed: true,
          rentDue: false,
          debtCharged: false,
          won: false,
          economy: { ...noEvent, event: 'BOOM' },
          state: stateFixture(),
        }}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText('Economic boom! Prices and wages have surged.')).toBeInTheDocument();
  });

  it('reports a major crash with firing and a bank wipe', () => {
    render(
      <EndWeekModal
        result={{
          round: 4,
          fed: true,
          rentDue: false,
          debtCharged: false,
          won: false,
          economy: {
            event: 'CRASH',
            severity: 'MAJOR',
            fired: true,
            wageCutTo: null,
            bankWiped: true,
            happinessLost: 3,
          },
          state: stateFixture(),
        }}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText('The market crashed! Prices tumble.')).toBeInTheDocument();
    expect(screen.getByText('You were laid off in the downturn.')).toBeInTheDocument();
    expect(screen.getByText('Your bank savings were wiped out.')).toBeInTheDocument();
  });

  it('reports a pay cut', () => {
    render(
      <EndWeekModal
        result={{
          round: 4,
          fed: true,
          rentDue: false,
          debtCharged: false,
          won: false,
          economy: {
            event: 'CRASH',
            severity: 'MODERATE',
            fired: false,
            wageCutTo: 8,
            bankWiped: false,
            happinessLost: 2,
          },
          state: stateFixture(),
        }}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText('Your pay was cut to R8/h.')).toBeInTheDocument();
  });
});
