import { apiFetch } from './http';
import type { EndWeekResponse, MoveResponse, PlayerStateDto } from './types';

export function getPlayerState(username: string): Promise<PlayerStateDto> {
  return apiFetch<PlayerStateDto>(`/players/${encodeURIComponent(username)}`);
}

export function move(username: string, target: string): Promise<MoveResponse> {
  return apiFetch<MoveResponse>(`/players/${encodeURIComponent(username)}/move`, {
    method: 'POST',
    body: { target },
  });
}

export function endWeek(username: string): Promise<EndWeekResponse> {
  return apiFetch<EndWeekResponse>(`/players/${encodeURIComponent(username)}/end-week`, {
    method: 'POST',
  });
}
