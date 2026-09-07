import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { useDictStore } from '@vben/stores';

import { useGridColumns } from './data';

describe('payment-application grid formatters', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useDictStore().setDictCache({
      finance_payment_reason: [{ label: '货款', value: 'goods' }],
      finance_product_type: [
        { label: '游戏', value: 'game' },
        { label: '广告', value: 'ads' },
      ],
    });
  });

  function format(field: string, cellValue: unknown) {
    const col = useGridColumns().find((c) => c.field === field) as {
      formatter?: (p: { cellValue: unknown }) => unknown;
    };
    return col.formatter?.({ cellValue });
  }

  it('does not throw when cost_project is null and product dict is loaded (prod 6/6 NULL)', () => {
    expect(() => format('costProject', null)).not.toThrow();
    expect(format('costProject', null)).toBe('-');
  });

  it('does not throw when paymentReason is null', () => {
    expect(() => format('paymentReason', null)).not.toThrow();
    expect(format('paymentReason', null)).toBe('-');
  });

  it('returns dict labels for known values and dash for empty string', () => {
    expect(format('costProject', 'game')).toBe('游戏');
    expect(format('paymentReason', 'goods')).toBe('货款');
    expect(format('costProject', '')).toBe('-');
    expect(format('costProject', 0)).toBe('-');
    expect(format('costProject', false)).toBe('-');
  });
});
