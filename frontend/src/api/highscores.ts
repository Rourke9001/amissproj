import { apiFetch } from './http';
import type { HighscoreEntry } from './types';

export function fetchHighscores(): Promise<HighscoreEntry[]> {
  return apiFetch<HighscoreEntry[]>('/highscores');
}
