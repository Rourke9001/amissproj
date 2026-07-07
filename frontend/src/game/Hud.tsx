import type { GoalDto, GoalsDto, PlayerStateDto } from '../api/types';
import { storefrontImage } from '../assets/manifest';

interface HudProps {
  player: PlayerStateDto;
  onEndWeek: () => void;
  endWeekPending: boolean;
}

const GOALS: { key: keyof GoalsDto; label: string }[] = [
  { key: 'cash', label: 'Cash' },
  { key: 'happiness', label: 'Happiness' },
  { key: 'workExperience', label: 'Work Experience' },
  { key: 'education', label: 'Education' },
];

function goalPercent(goal: GoalDto): number {
  if (goal.target <= 0) {
    return 100;
  }
  return Math.min(100, (goal.current / goal.target) * 100);
}

export function Hud({ player, onEndWeek, endWeekPending }: HudProps) {
  const { location, job, stats, goals } = player;

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
          <dd>
            {job === null
              ? 'Unemployed'
              : job.hourlyWage !== null && job.location
                ? `${job.name} R${job.hourlyWage}/h`
                : job.name}
          </dd>
        </div>
        <div className="hud-stat">
          <dt>Education</dt>
          <dd>
            {/* educationProgress mirrors UniversityService's 1-based `prog` out of 10 studies. */}
            Level {stats.education}
            {stats.educationProgress > 0 && ` (${stats.educationProgress}/10 studies)`}
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
          <dd>{stats.happiness}</dd>
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
