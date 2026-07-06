import { apiFetch } from './http';
import type { MoveResponse, PlayerStateDto } from './types';

export function getPlayerState(username: string): Promise<PlayerStateDto> {
  return apiFetch<PlayerStateDto>(`/players/${encodeURIComponent(username)}`);
}

export function move(username: string, target: string): Promise<MoveResponse> {
  return apiFetch<MoveResponse>(`/players/${encodeURIComponent(username)}/move`, {
    method: 'POST',
    body: { target },
  });
}
