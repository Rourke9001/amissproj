import { describe, expect, it } from 'vitest';
import { formatMinutes } from './formatMinutes';

describe('formatMinutes', () => {
  it('formats 0 minutes as "0h"', () => {
    expect(formatMinutes(0)).toBe('0h');
  });

  it('formats a sub-hour count with no hours part', () => {
    expect(formatMinutes(45)).toBe('45m');
  });

  it('formats an exact number of hours with no minutes part', () => {
    expect(formatMinutes(4320)).toBe('72h');
  });

  it('formats hours and minutes together', () => {
    expect(formatMinutes(2310)).toBe('38h 30m');
  });

  it('formats exactly one hour as "1h"', () => {
    expect(formatMinutes(60)).toBe('1h');
  });
});
