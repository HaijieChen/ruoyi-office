import { describe, expect, it } from 'vitest';

import { applyDependencyChange, buildDependencyOrder } from './linkage';

describe('buildDependencyOrder', () => {
  it('orders dependencies before their consumers', () => {
    expect(
      buildDependencyOrder([
        { dependencies: ['sealIds'], field: 'keeperName' },
        { dependencies: [], field: 'companyId' },
        { dependencies: ['companyId'], field: 'sealIds' },
      ]),
    ).toEqual(['companyId', 'sealIds', 'keeperName']);
  });

  it('includes external form dependencies as deterministic roots', () => {
    expect(
      buildDependencyOrder([
        { dependencies: ['companyId'], field: 'sealIds' },
        { dependencies: ['sealIds'], field: 'keeperName' },
      ]),
    ).toEqual(['companyId', 'sealIds', 'keeperName']);
  });

  it('rejects duplicate fields and dependency cycles', () => {
    expect(() =>
      buildDependencyOrder([
        { dependencies: [], field: 'companyId' },
        { dependencies: [], field: 'companyId' },
      ]),
    ).toThrow(/duplicate/i);

    expect(() =>
      buildDependencyOrder([
        { dependencies: ['sealIds'], field: 'companyId' },
        { dependencies: ['companyId'], field: 'sealIds' },
      ]),
    ).toThrow(/cycle/i);
  });
});

describe('applyDependencyChange', () => {
  it('clear-and-reload clears the target and every mapped field', () => {
    const result = applyDependencyChange({
      currentValue: [1, 2],
      multiple: true,
      options: [{ id: 1, keeperName: '张三' }],
      outputMappings: { keeperName: 'keeperName' },
      strategy: 'clear-and-reload',
      valueField: 'id',
    });

    expect(result).toEqual({
      mappedValues: { keeperName: undefined },
      reload: true,
      value: [],
    });
  });

  it('keep-and-revalidate retains only values present in the new options', () => {
    const currentValue = [1, 3];
    const result = applyDependencyChange({
      currentValue,
      multiple: true,
      options: [
        { id: 1, keeperName: '张三' },
        { id: 2, keeperName: '李四' },
      ],
      outputMappings: { keeperName: 'keeperNames' },
      strategy: 'keep-and-revalidate',
      valueField: 'id',
    });

    expect(result).toEqual({
      mappedValues: { keeperNames: ['张三'] },
      reload: true,
      value: [1],
    });
    expect(currentValue).toEqual([1, 3]);
  });

  it('revalidates a single value and refreshes mapped fields', () => {
    expect(
      applyDependencyChange({
        currentValue: 2,
        multiple: false,
        options: [{ id: 2, keeper: { name: '李四' } }],
        outputMappings: { 'keeper.name': 'keeperName' },
        strategy: 'keep-and-revalidate',
        valueField: 'id',
      }),
    ).toEqual({
      mappedValues: { keeperName: '李四' },
      reload: true,
      value: 2,
    });
  });

  it('clears a single value and mappings when it is no longer valid', () => {
    expect(
      applyDependencyChange({
        currentValue: 9,
        multiple: false,
        options: [{ id: 2, keeperName: '李四' }],
        outputMappings: { keeperName: 'keeperName' },
        strategy: 'keep-and-revalidate',
        valueField: 'id',
      }),
    ).toEqual({
      mappedValues: { keeperName: undefined },
      reload: true,
      value: undefined,
    });
  });
});
