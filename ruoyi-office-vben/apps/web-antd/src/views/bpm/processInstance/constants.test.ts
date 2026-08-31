import { describe, expect, it } from 'vitest';

import {
  isFinanceApprovalPShellViewPath,
  resolveBusinessFormViewPath,
} from './constants';

describe('resolveBusinessFormViewPath', () => {
  it('prefers a non-empty todo node path', () => {
    expect(
      resolveBusinessFormViewPath(
        { formCustomViewPath: '/finance/salary-payment/detail/cashier' },
        '/finance/salary-payment/detail/index',
      ),
    ).toEqual({
      path: '/finance/salary-payment/detail/cashier',
      source: 'node',
    });
  });

  it('falls back to the process path when the todo has no path', () => {
    expect(
      resolveBusinessFormViewPath(
        { formCustomViewPath: '  ' },
        '/finance/salary-payment/detail/index',
      ),
    ).toEqual({
      path: '/finance/salary-payment/detail/index',
      source: 'process',
    });
  });

  it('falls back when there is no todo task', () => {
    expect(
      resolveBusinessFormViewPath(null, '/finance/payment-application/detail/index'),
    ).toEqual({
      path: '/finance/payment-application/detail/index',
      source: 'process',
    });
  });
});

describe('isFinanceApprovalPShellViewPath', () => {
  it('keeps matching the process-level payment view', () => {
    expect(
      isFinanceApprovalPShellViewPath('/finance/salary-payment/detail/index'),
    ).toBe(true);
  });

  it('does not require node cashier/finance paths on the whitelist', () => {
    expect(
      isFinanceApprovalPShellViewPath('/finance/salary-payment/detail/cashier'),
    ).toBe(false);
  });
});
