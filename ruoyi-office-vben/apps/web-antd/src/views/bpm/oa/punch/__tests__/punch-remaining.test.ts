import { describe, expect, it } from 'vitest';

import {
  remainingLabel,
  shouldFetchRemaining,
  toPunchDateStr,
} from '../punch-remaining';

describe('punch remaining display', () => {
  it('does not show a fake remaining count without a punch date', () => {
    expect(remainingLabel(undefined, 2)).toBeUndefined();
    expect(remainingLabel('', 2)).toBeUndefined();
    expect(remainingLabel(null, 0)).toBeUndefined();
  });

  it('shows remaining after a punch date is chosen, including zero', () => {
    expect(remainingLabel('2026-08-31', 2)).toBe('本月剩余 2 次');
    expect(remainingLabel('2026-08-31', 1)).toBe('本月剩余 1 次');
    expect(remainingLabel('2026-08-31', 0)).toBe('本月剩余 0 次');
  });

  it('does not invent remaining while the count is still unknown', () => {
    expect(remainingLabel('2026-08-31', undefined)).toBeUndefined();
    expect(remainingLabel('2026-08-31', null)).toBeUndefined();
    expect(remainingLabel('2026-08-31', Number.NaN)).toBeUndefined();
  });
});

describe('punch remaining fetch', () => {
  it('does not fetch without a date', () => {
    expect(shouldFetchRemaining(undefined, undefined)).toBe(false);
    expect(shouldFetchRemaining('', '2026-08-31')).toBe(false);
  });

  it('fetches when the punch date changes', () => {
    expect(shouldFetchRemaining('2026-08-31', undefined)).toBe(true);
    expect(shouldFetchRemaining('2026-09-01', '2026-08-31')).toBe(true);
  });

  it('does not refetch the same date', () => {
    expect(shouldFetchRemaining('2026-08-31', '2026-08-31')).toBe(false);
  });
});

describe('toPunchDateStr', () => {
  it('normalizes date-like values to YYYY-MM-DD', () => {
    expect(toPunchDateStr('2026-08-31')).toBe('2026-08-31');
    expect(toPunchDateStr('2026-08-31T10:00:00')).toBe('2026-08-31');
    expect(toPunchDateStr('')).toBeUndefined();
    expect(toPunchDateStr(undefined)).toBeUndefined();
  });
});
