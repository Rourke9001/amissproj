import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { HomePanel } from './HomePanel';
import type { PlayerStateDto } from '../../api/types';

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
    location: { id: 'LOW_COST_HOUSING', name: 'Low-Cost Housing', ringIndex: 0, row: 0, col: 2 },
    ...overrides,
  };
}

describe('HomePanel', () => {
  it('shows the Home heading and the food-stored line', () => {
    render(
      <HomePanel username="alice" player={playerFixture({ foodWeeks: 3 })} onNotify={vi.fn()} />,
    );

    expect(screen.getByRole('heading', { name: 'Home' })).toBeInTheDocument();
    expect(screen.getByText('Food stored: 3 wk')).toBeInTheDocument();
  });

  it('shows the rent-due line when rent is due', () => {
    render(
      <HomePanel username="alice" player={playerFixture({ rentDue: true })} onNotify={vi.fn()} />,
    );

    expect(
      screen.getByText('Rent is due — the Rent Office expects R80 this round.'),
    ).toBeInTheDocument();
  });

  it('omits the rent-due line when rent is not due', () => {
    render(
      <HomePanel username="alice" player={playerFixture({ rentDue: false })} onNotify={vi.fn()} />,
    );

    expect(
      screen.queryByText('Rent is due — the Rent Office expects R80 this round.'),
    ).not.toBeInTheDocument();
  });
});
