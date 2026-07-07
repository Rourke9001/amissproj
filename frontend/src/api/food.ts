import { apiFetch } from './http';
import type { EatResponse, FoodCatalogDto, GroceriesResponse } from './types';

export function getFoodCatalog(): Promise<FoodCatalogDto> {
  return apiFetch<FoodCatalogDto>('/food');
}

export function eat(username: string, item: string): Promise<EatResponse> {
  return apiFetch<EatResponse>(`/players/${encodeURIComponent(username)}/eat`, {
    method: 'POST',
    body: { item },
  });
}

export function buyGroceries(username: string, pack: string): Promise<GroceriesResponse> {
  return apiFetch<GroceriesResponse>(`/players/${encodeURIComponent(username)}/groceries`, {
    method: 'POST',
    body: { pack },
  });
}
