import { apiFetch } from './http';
import type { ClothesResponse, ClothingItemDto } from './types';

export function getClothesCatalog(): Promise<ClothingItemDto[]> {
  return apiFetch<ClothingItemDto[]>('/clothes');
}

export function buyClothes(username: string, item: string): Promise<ClothesResponse> {
  return apiFetch<ClothesResponse>(`/players/${encodeURIComponent(username)}/clothes`, {
    method: 'POST',
    body: { item },
  });
}
