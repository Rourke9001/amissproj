import { describe, expect, it } from 'vitest';
import { resolvePanel, DefaultPanel } from './registry';
import { BankPanel } from './BankPanel';
import { RentOfficePanel } from './RentOfficePanel';

describe('resolvePanel', () => {
  it('resolves BANK to BankPanel', () => {
    expect(resolvePanel('BANK')).toBe(BankPanel);
  });

  it('resolves RENT_OFFICE to RentOfficePanel', () => {
    expect(resolvePanel('RENT_OFFICE')).toBe(RentOfficePanel);
  });

  it('falls back to DefaultPanel for an unknown location id', () => {
    expect(resolvePanel('PAWN_SHOP')).toBe(DefaultPanel);
  });
});
