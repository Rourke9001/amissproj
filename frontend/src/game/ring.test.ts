import { describe, expect, it } from 'vitest';
import { ringSteps } from './ring';

describe('ringSteps', () => {
  it('is 1 for adjacent stops', () => {
    expect(ringSteps(0, 1, 13)).toBe(1);
  });

  it('wraps around: index 0 and 12 of 13 are 1 apart', () => {
    expect(ringSteps(0, 12, 13)).toBe(1);
  });

  it('is the ring radius for the cross-town case', () => {
    expect(ringSteps(0, 6, 13)).toBe(6);
  });

  it('is 0 for the same stop', () => {
    expect(ringSteps(4, 4, 13)).toBe(0);
  });
});
