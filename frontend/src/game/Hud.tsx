import type { GoalDto, GoalsDto, SaveStateDto } from '../api/types';
import { storefrontImage } from '../assets/manifest';

interface HudProps {
  player: SaveStateDto;
  onEndWeek: () => void;
  endWeekPending: boolean;
}

const GOALS: { key: keyof GoalsDto; label: string }[] = [
  { key: 'wealth', label: 'Wealth' },
  { key: 'happiness', label: 'Happiness' },
  { key: 'education', label: 'Education' },
  { key: 'career', label: 'Career' },
];

function goalPercent(goal: GoalDto): number {
  if (goal.target <= 0) {
    return 100;
  }
  return Math.min(100, (goal.current / goal.target) * 100);
}

export function Hud({ player, onEndWeek, endWeekPending }: HudProps) {
  const { location, job, goals } = player;

  return (
    <div className="hud">
      <div className="hud-header">
        <img src={storefrontImage(location.id)} alt={location.name} className="hud-thumbnail" />
        <p className="hud-location-name">{location.name}</p>
      </div>

      <dl className="hud-stats">
        <div className="hud-stat">
          <dt>Cash</dt>
          <dd>R{player.cash}</dd>
        </div>
        <div className="hud-stat">
          <dt>Bank</dt>
          <dd>R{player.bank}</dd>
        </div>
        {player.debt > 0 && (
          <div className="hud-stat">
            <dt>Debt</dt>
            <dd>R{player.debt}</dd>
          </div>
        )}
        <div className="hud-stat">
          <dt>Job</dt>
          <dd>{job.hourlyWage === null ? job.name : `${job.name} R${job.hourlyWage}/h`}</dd>
        </div>
        <div className="hud-stat">
          <dt>Education</dt>
          <dd>
            {player.degreesEarned.length > 0 ? player.degreesEarned.join(', ') : 'None yet'}
            {player.currentCourse !== null &&
              ` — Studying ${player.currentCourse.name} (${player.currentCourse.studiesDone}/10)`}
          </dd>
        </div>
        <div className="hud-stat">
          <dt>Clothing</dt>
          <dd>Level {player.clothing}</dd>
        </div>
        <div className="hud-stat">
          <dt>Food</dt>
          <dd>{player.foodWeeks} wk</dd>
        </div>
        <div className="hud-stat">
          <dt>Happiness</dt>
          <dd>{goals.happiness.current}</dd>
        </div>
      </dl>

      <div className="hud-goals">
        {GOALS.map(({ key, label }) => {
          const goal = goals[key];
          return (
            <div className="goal-bar" key={key}>
              <div className="goal-bar-label">
                <span>{label}</span>
                <span>
                  {goal.current} / {goal.target}
                </span>
              </div>
              <div className="goal-bar-track">
                <div className="goal-bar-fill" style={{ width: `${goalPercent(goal)}%` }} />
              </div>
            </div>
          );
        })}
      </div>

      {player.rentDue && (
        <p className="hud-warning" role="alert">
          Rent is due this round — visit the Rent Office.
        </p>
      )}

      {player.weekOver && (
        <button
          type="button"
          className="hud-end-week"
          onClick={onEndWeek}
          disabled={endWeekPending}
        >
          End Week
        </button>
      )}
    </div>
  );
}
