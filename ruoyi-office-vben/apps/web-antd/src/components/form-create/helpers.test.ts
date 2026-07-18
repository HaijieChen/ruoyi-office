import { describe, expect, it } from 'vitest';

import { hydrateRemoteDataSourceRules } from './helpers';

describe('hydrateRemoteDataSourceRules', () => {
  it('hydrates every rule container traversed by the backend parser', () => {
    const context = { formId: 42, taskId: 'task-1' } as const;
    const remote = () => ({
      field: crypto.randomUUID(),
      props: {} as Record<string, any>,
      type: 'RemoteDataSourceSelect',
    });
    const child = remote();
    const groupRule = remote();
    const tableRule = remote();
    const controlRule = remote();
    const rules = [
      {
        children: [child],
        control: [{ rule: [controlRule] }],
        props: { rule: [groupRule] },
        type: 'group',
      },
      {
        props: { columns: [{ rule: [tableRule] }] },
        type: 'tableForm',
      },
    ];

    hydrateRemoteDataSourceRules(rules, context);

    for (const rule of [child, groupRule, tableRule, controlRule]) {
      expect(rule.props.runtimeContext).toBe(context);
    }
  });

  it('removes runtime context from read-only history rules', () => {
    const rule = {
      props: { runtimeContext: { formId: 42, taskId: 'task-1' } },
      type: 'RemoteDataSourceSelect',
    };

    hydrateRemoteDataSourceRules([rule], undefined);

    expect(rule.props).not.toHaveProperty('runtimeContext');
  });
});
