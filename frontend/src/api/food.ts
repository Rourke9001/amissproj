import { apiFetch } from './http';
import type { EatResponse, FoodCatalogDto, GroceriesResponse } from './types';

export function getFoodCatalog(saveId: number): Promise<FoodCatalogDto> {
  return apiFetch<FoodCatalogDto>(`/saves/${saveId}/food`);
}

export function eat(saveId: number, item: string): Promise<EatResponse> {
  return apiFetch<EatResponse>(`/saves/${saveId}/eat`, {
    method: 'POST',
    body: { item },
  });
}

export function buyGroceries(saveId: number, pack: string): Promise<GroceriesResponse> {
  return apiFetch<GroceriesResponse>(`/saves/${saveId}/groceries`, {
    method: 'POST',
    body: { pack },
  });
}
