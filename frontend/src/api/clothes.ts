import { apiFetch } from './http';
import type { ClothesResponse, ClothingItemDto } from './types';

export function getClothesCatalog(saveId: number): Promise<ClothingItemDto[]> {
  return apiFetch<ClothingItemDto[]>(`/saves/${saveId}/clothes`);
}

export function buyClothes(saveId: number, item: string): Promise<ClothesResponse> {
  return apiFetch<ClothesResponse>(`/saves/${saveId}/clothes`, {
    method: 'POST',
    body: { item },
  });
}
