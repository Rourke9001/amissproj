// Per-location asset manifest. Real art drops into frontend/public/assets/*
// under these same names with zero code changes (placeholders are regenerated
// with scripts/gen-frontend-placeholders.ps1).

export const BOARD_IMAGE = '/assets/board/board.png';

/** `/assets/locations/<kebab-id>.png`, e.g. LOW_COST_HOUSING -> low-cost-housing.png. */
export function storefrontImage(locationId: string): string {
  const kebab = locationId.toLowerCase().replace(/_/g, '-');
  return `/assets/locations/${kebab}.png`;
}
