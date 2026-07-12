import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { createSave } from '../api/saves';
import { errorMessage } from '../api/http';
import type { GoalTargets } from '../api/types';

const MIN_GOAL = 10;
const MAX_GOAL = 100;

interface NewGameSetupProps {
  onCreated: (saveId: number) => void;
  onCancel: () => void;
}

function randomGoal(): number {
  return MIN_GOAL + Math.floor(Math.random() * (MAX_GOAL - MIN_GOAL + 1));
}

const SLIDERS: { key: keyof GoalTargets; label: string }[] = [
  { key: 'wealth', label: 'Wealth' },
  { key: 'happiness', label: 'Happiness' },
  { key: 'education', label: 'Education' },
  { key: 'career', label: 'Career' },
];

export function NewGameSetup({ onCreated, onCancel }: NewGameSetupProps) {
  const [label, setLabel] = useState('');
  const [goals, setGoals] = useState<GoalTargets>({
    wealth: 50,
    happiness: 50,
    education: 50,
    career: 50,
  });

  const createMutation = useMutation({
    mutationFn: () => createSave({ label: label.trim() || 'Save 1', goals }),
    onSuccess: (save) => onCreated(save.id),
  });

  function updateGoal(key: keyof GoalTargets, value: number) {
    setGoals((prev) => ({ ...prev, [key]: value }));
  }

  function randomise() {
    setGoals({
      wealth: randomGoal(),
      happiness: randomGoal(),
      education: randomGoal(),
      career: randomGoal(),
    });
  }

  return (
    <section className="new-game-setup">
      <h1>New Game</h1>
      <label className="field">
        Save name
        <input
          type="text"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          placeholder="Save 1"
          maxLength={100}
        />
      </label>
      {SLIDERS.map(({ key, label: sliderLabel }) => (
        <label key={key} className="field goal-slider">
          {sliderLabel}: {goals[key]}
          <input
            type="range"
            min={MIN_GOAL}
            max={MAX_GOAL}
            value={goals[key]}
            onChange={(e) => updateGoal(key, Number(e.target.value))}
          />
        </label>
      ))}
      <div className="new-game-actions">
        <button type="button" onClick={randomise}>
          Randomise
        </button>
        <button
          type="button"
          disabled={createMutation.isPending}
          onClick={() => createMutation.mutate()}
        >
          Start Game
        </button>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
      {createMutation.isError && <p role="alert">{errorMessage(createMutation.error)}</p>}
    </section>
  );
}
