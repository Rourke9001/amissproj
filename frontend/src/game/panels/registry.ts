import type { ComponentType } from 'react';
import { BankPanel } from './BankPanel';
import { RentOfficePanel } from './RentOfficePanel';
import { EmploymentOfficePanel } from './EmploymentOfficePanel';
import { UniversityPanel } from './UniversityPanel';
import { MonolithBurgersPanel } from './MonolithBurgersPanel';
import { BlacksMarketPanel } from './BlacksMarketPanel';
import { QTClothingPanel } from './QTClothingPanel';
import { SocketCityPanel } from './SocketCityPanel';
import { HomePanel } from './HomePanel';
import { DefaultPanel } from './DefaultPanel';
import type { PanelProps } from './types';

// Lookup from a LocationDto.id (the domain.board.Location enum name) to the
// panel component rendered for that stop. Stops with no dedicated panel yet
// fall back to DefaultPanel.
const PANEL_REGISTRY: Record<string, ComponentType<PanelProps>> = {
  BANK: BankPanel,
  RENT_OFFICE: RentOfficePanel,
  EMPLOYMENT_OFFICE: EmploymentOfficePanel,
  HI_TECH_U: UniversityPanel,
  MONOLITH_BURGERS: MonolithBurgersPanel,
  BLACKS_MARKET: BlacksMarketPanel,
  QT_CLOTHING: QTClothingPanel,
  SOCKET_CITY: SocketCityPanel,
  LOW_COST_HOUSING: HomePanel,
};

export function resolvePanel(locationId: string): ComponentType<PanelProps> {
  return PANEL_REGISTRY[locationId] ?? DefaultPanel;
}

export { DefaultPanel };
export type { PanelProps };
