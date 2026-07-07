import type { PanelProps } from './types';

// Purely informational for now — resting and home upgrades arrive with the
// KAN-5 economy epic; this just surfaces the player's food/rent state.
export function HomePanel({ player }: PanelProps) {
  return (
    <div className="home-panel">
      <h2>Home</h2>
      <p>Food stored: {player.foodWeeks} wk</p>
      {player.rentDue && <p>Rent is due — the Rent Office expects R80 this round.</p>}
      <p className="panel-muted">Resting and home upgrades arrive with the KAN-5 economy epic.</p>
    </div>
  );
}
