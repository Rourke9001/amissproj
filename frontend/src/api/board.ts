import { apiFetch } from './http';
import type { BoardDto } from './types';

export function getBoard(): Promise<BoardDto> {
  return apiFetch<BoardDto>('/board');
}
