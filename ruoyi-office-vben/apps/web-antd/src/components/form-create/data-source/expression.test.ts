import { describe, expect, it } from 'vitest';

import { resolveBinding } from './expression';

const context = {
  FORM: {
    companyId: 9,
    customer: { id: 42, name: '客户 A' },
  },
  PROCESS: {
    definitionKey: 'oa_seal_general',
    instanceId: 'process-1',
  },
  USER: {
    companyId: 9,
    deptId: 3,
    id: 7,
  },
};

describe('resolveBinding', () => {
  it('resolves allowed form paths', () => {
    expect(resolveBinding('FORM.companyId', context)).toBe(9);
    expect(resolveBinding('FORM.customer.id', context)).toBe(42);
  });

  it('resolves only the allowed user and process fields', () => {
    expect(resolveBinding('USER.id', context)).toBe(7);
    expect(resolveBinding('USER.deptId', context)).toBe(3);
    expect(resolveBinding('USER.companyId', context)).toBe(9);
    expect(resolveBinding('PROCESS.definitionKey', context)).toBe(
      'oa_seal_general',
    );
    expect(resolveBinding('PROCESS.instanceId', context)).toBe('process-1');
  });

  it.each([
    'alert(1)',
    'FORM.companyId || USER.id',
    'FORM.companyId = 1',
    'FORM["companyId"]',
    'FORM.__proto__.polluted',
    'FORM.customer.prototype',
    'FORM.customer.constructor',
    'USER.password',
    'USER.id.value',
    'PROCESS.businessKey',
    'FORM',
  ])('rejects unsafe or unsupported binding %s', (binding) => {
    expect(() => resolveBinding(binding, context)).toThrow(/binding/i);
  });

  it('does not traverse inherited properties', () => {
    const form = Object.create({ inherited: 'secret' }) as Record<
      string,
      unknown
    >;
    form.own = 'visible';

    expect(resolveBinding('FORM.own', { ...context, FORM: form })).toBe(
      'visible',
    );
    expect(resolveBinding('FORM.inherited', { ...context, FORM: form })).toBe(
      undefined,
    );
  });
});
