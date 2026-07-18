import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { HomePanel } from './HomePanel';
import type { SaveStateDto } from '../../api/types';

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
    location: { id: 'LOW_COST_HOUSING', name: 'Low-Cost Housing', ringIndex: 0, row: 0, col: 2 },
    ...overrides,
  };
}

describe('HomePanel', () => {
  it('shows the Home heading and the food-stored line', () => {
    render(<HomePanel saveId={42} player={playerFixture({ foodWeeks: 3 })} onNotify={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Home' })).toBeInTheDocument();
    expect(screen.getByText('Food stored: 3 wk')).toBeInTheDocument();
  });

  it('shows the rent-due line when rent is due', () => {
    render(<HomePanel saveId={42} player={playerFixture({ rentDue: true })} onNotify={vi.fn()} />);

    expect(
      screen.getByText('Rent is due — the Rent Office expects R80 this round.'),
    ).toBeInTheDocument();
  });

  it('omits the rent-due line when rent is not due', () => {
    render(<HomePanel saveId={42} player={playerFixture({ rentDue: false })} onNotify={vi.fn()} />);

    expect(
      screen.queryByText('Rent is due — the Rent Office expects R80 this round.'),
    ).not.toBeInTheDocument();
  });
});
