import { describe, expect, it } from 'vitest';

import { shouldSuppressErrorToast } from './error-toast';

describe('shouldSuppressErrorToast', () => {
  it('toasts hideErrorMessage 403 when msg is R1', () => {
    expect(
      shouldSuppressErrorToast({
        config: { hideErrorMessage: true },
        data: {
          code: 403,
          msg: '缺少权限「付款申请-发起」（finance:payment-application:create）',
        },
      }),
    ).toBe(false);
  });

  it('still swallows hideErrorMessage generic 403', () => {
    expect(
      shouldSuppressErrorToast({
        config: { hideErrorMessage: true },
        data: { code: 403, msg: '没有该操作权限' },
      }),
    ).toBe(true);
  });

  it('toasts R1 403 from response.data even with X-Hide-Error-Message', () => {
    expect(
      shouldSuppressErrorToast({
        config: { headers: { 'X-Hide-Error-Message': '1' } },
        response: {
          data: { code: 403, msg: '缺少权限（finance:payment-application:query）' },
        },
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

  it('toasts generic 403 when hideErrorMessage is not set', () => {
    expect(shouldSuppressErrorToast({ data: { code: 403, msg: '没有该操作权限' } })).toBe(
      false,
    );
  });
});
