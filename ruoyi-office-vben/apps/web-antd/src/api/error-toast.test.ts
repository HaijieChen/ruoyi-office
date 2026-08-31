import { describe, expect, it } from 'vitest';

import { shouldSuppressErrorToast } from './error-toast';

describe('shouldSuppressErrorToast', () => {
  it('toasts biz 403 even when hideErrorMessage is set', () => {
    expect(
      shouldSuppressErrorToast({
        config: { hideErrorMessage: true },
        data: { code: 403 },
      }),
    ).toBe(false);
  });

  it('toasts 403 from response.data.code', () => {
    expect(
      shouldSuppressErrorToast({
        config: { headers: { 'X-Hide-Error-Message': '1' } },
        response: { data: { code: 403 } },
      }),
    ).toBe(false);
  });

  it('still suppresses hideErrorMessage on 500', () => {
    expect(
      shouldSuppressErrorToast({
        config: { hideErrorMessage: true },
        data: { code: 500 },
      }),
    ).toBe(true);
  });

  it('still suppresses 401 so login hop is not double-toasted', () => {
    expect(shouldSuppressErrorToast({ data: { code: 401 } })).toBe(true);
  });
});
