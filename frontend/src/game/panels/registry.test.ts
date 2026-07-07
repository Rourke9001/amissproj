import { describe, expect, it } from 'vitest';
import { resolvePanel, DefaultPanel } from './registry';
import { BankPanel } from './BankPanel';
import { RentOfficePanel } from './RentOfficePanel';
import { MonolithBurgersPanel } from './MonolithBurgersPanel';
import { BlacksMarketPanel } from './BlacksMarketPanel';
import { QTClothingPanel } from './QTClothingPanel';
import { HomePanel } from './HomePanel';

describe('resolvePanel', () => {
  it('resolves BANK to BankPanel', () => {
    expect(resolvePanel('BANK')).toBe(BankPanel);
  });

  it('resolves RENT_OFFICE to RentOfficePanel', () => {
    expect(resolvePanel('RENT_OFFICE')).toBe(RentOfficePanel);
  });

  it('resolves MONOLITH_BURGERS to MonolithBurgersPanel', () => {
    expect(resolvePanel('MONOLITH_BURGERS')).toBe(MonolithBurgersPanel);
  });

  it('resolves BLACKS_MARKET to BlacksMarketPanel', () => {
    expect(resolvePanel('BLACKS_MARKET')).toBe(BlacksMarketPanel);
  });

  it('resolves QT_CLOTHING to QTClothingPanel', () => {
    expect(resolvePanel('QT_CLOTHING')).toBe(QTClothingPanel);
  });

  it('resolves LOW_COST_HOUSING to HomePanel', () => {
    expect(resolvePanel('LOW_COST_HOUSING')).toBe(HomePanel);
  });

  it('falls back to DefaultPanel for an unknown location id', () => {
    expect(resolvePanel('PAWN_SHOP')).toBe(DefaultPanel);
  });
});
