import type { ComponentType } from 'react';
import { BankPanel } from './BankPanel';
import { RentOfficePanel } from './RentOfficePanel';
import { DefaultPanel } from './DefaultPanel';
import type { PanelProps } from './types';

// Lookup from a LocationDto.id (the domain.board.Location enum name) to the
// panel component rendered for that stop. Stops with no dedicated panel yet
// fall back to DefaultPanel.
const PANEL_REGISTRY: Record<string, ComponentType<PanelProps>> = {
  BANK: BankPanel,
  RENT_OFFICE: RentOfficePanel,
};

export function resolvePanel(locationId: string): ComponentType<PanelProps> {
  return PANEL_REGISTRY[locationId] ?? DefaultPanel;
}

export { DefaultPanel };
export type { PanelProps };
