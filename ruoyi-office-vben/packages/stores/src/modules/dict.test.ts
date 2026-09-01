import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { shouldRefreshDictCache, useDictStore } from './dict';

describe('shouldRefreshDictCache', () => {
  it('refreshes authenticated app routes even after access is already checked', () => {
    expect(
      shouldRefreshDictCache({
        hasAccessToken: true,
        isCoreRoute: false,
        isAccessChecked: true,
      }),
    ).toBe(true);
  });

  it('does not refresh login or other core routes', () => {
    expect(
      shouldRefreshDictCache({
        hasAccessToken: true,
        isCoreRoute: true,
        isAccessChecked: false,
      }),
    ).toBe(false);
  });

  it('does not refresh when logged out', () => {
    expect(
      shouldRefreshDictCache({
        hasAccessToken: false,
        isCoreRoute: false,
        isAccessChecked: false,
      }),
    ).toBe(false);
  });
});

describe('useDictStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('replaces persisted dict cache with the latest API payload', async () => {
    const store = useDictStore();
    store.setDictCache({
      finance_expense_subitem: [{ label: '旧子项目', value: 'travel.old' }],
    });

    await store.setDictCacheByApi(async () => [
      {
        dictType: 'finance_expense_subitem',
        label: '新子项目',
        value: 'travel.new',
      },
    ]);

    expect(store.getDictOptions('finance_expense_subitem')).toEqual([
      {
        colorType: undefined,
        cssClass: undefined,
        label: '新子项目',
        value: 'travel.new',
      },
    ]);
  });

  it('ignores a stale in-flight refresh', async () => {
    const store = useDictStore();
    let resolveFirst!: (value: Record<string, any>[]) => void;
    const first = new Promise<Record<string, any>[]>((resolve) => {
      resolveFirst = resolve;
    });
    const second = Promise.resolve([
      {
        dictType: 'finance_expense_subitem',
        label: '第二次',
        value: 'travel.second',
      },
    ]);

    const firstRun = store.setDictCacheByApi(() => first);
    const secondRun = store.setDictCacheByApi(() => second);
    await secondRun;
    resolveFirst([
      {
        dictType: 'finance_expense_subitem',
        label: '第一次',
        value: 'travel.first',
      },
    ]);
    await firstRun;

    expect(
      store.getDictOptions('finance_expense_subitem').map((d) => d.value),
    ).toEqual(['travel.second']);
  });
});
