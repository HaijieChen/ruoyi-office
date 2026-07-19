import { describe, expect, it, vi } from 'vitest';

import { useFormSchema } from './data';

vi.mock('@vben/hooks', () => ({
  getDictOptions: () => [],
}));

describe('ERP customer form schema', () => {
  it('provides a mailing address field that can be persisted with the customer', () => {
    expect(useFormSchema()).toContainEqual(
      expect.objectContaining({
        component: 'Input',
        fieldName: 'mailingAddress',
        label: '邮寄地址',
      }),
    );
  });
});
