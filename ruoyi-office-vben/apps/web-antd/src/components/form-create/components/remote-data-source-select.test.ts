/* eslint-disable vue/one-component-per-file */
import type { Ref } from 'vue';

import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h, nextTick, ref } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { applyDependencyChange } from '../data-source/linkage';
import {
  buildExecuteParams,
  buildSelectionSnapshot,
  validateSelectorParamName,
} from '../data-source/selector';
import RemoteDataSourceSelect from './remote-data-source-select.vue';

const mocks = vi.hoisted(() => ({
  execute: vi.fn(),
  post: vi.fn(),
}));

vi.mock('@vben/stores', () => ({
  useUserStore: () => ({
    userInfo: { companyId: 9, deptId: 3, id: 7 },
  }),
}));

vi.mock('#/api/bpm/form-data-source', () => ({
  executePublishedDataSource: mocks.execute,
}));

vi.mock('#/api/request', () => ({
  requestClient: { post: mocks.post },
}));

const SelectStub = defineComponent({
  name: 'RemoteSelectTestStub',
  emits: ['change', 'popupScroll', 'search'],
  setup(_, { slots }) {
    return () =>
      h('div', { 'data-testid': 'remote-select' }, slots.default?.());
  },
});

const SelectOptionStub = defineComponent({
  name: 'RemoteSelectOptionTestStub',
  setup(_, { slots }) {
    return () => h('span', slots.default?.());
  },
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

function mountSelector(options: {
  companyId?: Ref<number | undefined>;
  getValue?: (field: string) => unknown;
  modelValue?: unknown;
  props?: Record<string, unknown>;
  setValue?: ReturnType<typeof vi.fn>;
}) {
  const companyId = options.companyId ?? ref(8);
  const setValue = options.setValue ?? vi.fn();
  const formCreateInject = {
    api: {
      formData: () => ({ companyId: companyId.value }),
      getValue:
        options.getValue ??
        ((field: string) =>
          field === 'companyId' ? companyId.value : undefined),
      setValue,
    },
  };
  const wrapper = mount(RemoteDataSourceSelect, {
    global: {
      stubs: {
        ASelect: SelectStub,
        ASelectOption: SelectOptionStub,
        ASpin: true,
        Select: SelectStub,
        SelectOption: SelectOptionStub,
        Spin: true,
      },
    },
    props: {
      dataSourceCode: 'oa_available_seals',
      dependencies: ['companyId'],
      formCreateInject,
      labelField: 'name',
      modelValue: options.modelValue,
      multiple: true,
      onDependencyChange: 'clear-and-reload',
      outputMappings: { keeperName: 'keeperName' },
      parameterBindings: { selectedCompanyId: 'FORM.companyId' },
      runtimeContext: { formId: 42, processDefinitionId: 'definition-1' },
      valueField: 'id',
      ...options.props,
    },
  });
  return { companyId, setValue, wrapper };
}

beforeEach(() => {
  mocks.execute.mockReset().mockResolvedValue({
    rows: [{ id: 1, keeperName: '张三', name: '公章' }],
    total: 1,
    version: 1,
  });
  mocks.post.mockReset().mockResolvedValue({
    rows: [{ id: 1, keeperName: '张三', name: '公章' }],
    total: 1,
    version: 1,
  });
});

describe('remoteDataSourceSelect component', () => {
  it('waits for an empty dependency before executing the data source', async () => {
    const companyId = ref<number>();
    const { wrapper } = mountSelector({ companyId });
    await flushPromises();

    expect(mocks.execute).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('加载数据源失败');

    companyId.value = 9;
    await nextTick();
    await flushPromises();

    expect(mocks.execute).toHaveBeenCalledTimes(1);
    expect(mocks.execute).toHaveBeenCalledWith('oa_available_seals', {
      formId: 42,
      params: { selectedCompanyId: 9 },
      processDefinitionId: 'definition-1',
    });
  });

  it('sends the trusted body and clears value/mappings after dependency changes', async () => {
    const companyId = ref(8);
    const { setValue, wrapper } = mountSelector({
      companyId,
      modelValue: [1],
    });
    await flushPromises();
    mocks.execute.mockClear();
    setValue.mockClear();

    companyId.value = 9;
    await nextTick();
    await flushPromises();

    expect(mocks.execute).toHaveBeenCalledWith('oa_available_seals', {
      formId: 42,
      params: { selectedCompanyId: 9 },
      processDefinitionId: 'definition-1',
    });
    expect(wrapper.emitted('update:modelValue')).toContainEqual([[]]);
    expect(setValue).toHaveBeenCalledWith('keeperName', undefined);
  });

  it('does not execute when runtime context is missing formId or contains both trusted ids', async () => {
    const missingForm = mountSelector({
      props: { runtimeContext: { processDefinitionId: 'definition-1' } },
    });
    const bothIds = mountSelector({
      props: {
        runtimeContext: {
          formId: 42,
          processDefinitionId: 'definition-1',
          taskId: 'task-1',
        },
      },
    });
    await flushPromises();

    expect(mocks.execute).not.toHaveBeenCalled();
    expect(mocks.post).not.toHaveBeenCalled();
    missingForm.wrapper.unmount();
    bothIds.wrapper.unmount();
  });

  it('ignores an older response after a dependency reload', async () => {
    const first = deferred<any>();
    const second = deferred<any>();
    mocks.execute
      .mockReset()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const companyId = ref(8);
    const { wrapper } = mountSelector({ companyId });
    await nextTick();
    companyId.value = 9;
    await nextTick();

    expect(mocks.execute).toHaveBeenCalledTimes(2);
    second.resolve({
      rows: [{ id: 2, name: '新结果' }],
      total: 1,
      version: 1,
    });
    await flushPromises();
    first.resolve({
      rows: [{ id: 1, name: '旧结果' }],
      total: 1,
      version: 1,
    });
    await flushPromises();

    expect(wrapper.text()).toContain('新结果');
    expect(wrapper.text()).not.toContain('旧结果');
  });

  it('loads and appends the next page on popup scroll', async () => {
    mocks.execute
      .mockReset()
      .mockResolvedValueOnce({
        rows: [{ id: 1, name: '第一页' }],
        total: 2,
        version: 1,
      })
      .mockResolvedValueOnce({
        rows: [{ id: 2, name: '第二页' }],
        total: 2,
        version: 1,
      });
    const { wrapper } = mountSelector({
      props: {
        pageNoParamName: 'pageNo',
        pageSize: 1,
        pageSizeParamName: 'pageSize',
        pageable: true,
      },
    });
    await flushPromises();

    wrapper.findComponent(SelectStub).vm.$emit('popupScroll', {
      target: { clientHeight: 100, scrollHeight: 180, scrollTop: 80 },
    });
    await flushPromises();

    expect(mocks.execute).toHaveBeenLastCalledWith('oa_available_seals', {
      formId: 42,
      params: { pageNo: 2, pageSize: 1, selectedCompanyId: 8 },
      processDefinitionId: 'definition-1',
    });
    expect(wrapper.text()).toContain('第一页');
    expect(wrapper.text()).toContain('第二页');
  });

  it('retries a failed next page without replacing the first page', async () => {
    mocks.execute
      .mockReset()
      .mockResolvedValueOnce({
        rows: [{ id: 1, name: '第一页' }],
        total: 2,
        version: 1,
      })
      .mockRejectedValueOnce(new Error('第二页失败'))
      .mockResolvedValueOnce({
        rows: [{ id: 2, name: '第二页' }],
        total: 2,
        version: 1,
      });
    const { wrapper } = mountSelector({
      props: {
        pageNoParamName: 'pageNo',
        pageSize: 1,
        pageSizeParamName: 'pageSize',
        pageable: true,
      },
    });
    await flushPromises();
    wrapper.findComponent(SelectStub).vm.$emit('popupScroll', {
      target: { clientHeight: 100, scrollHeight: 180, scrollTop: 80 },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('第一页');
    expect(wrapper.text()).toContain('第二页失败');
    await wrapper.find('.text-red-500').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('第一页');
    expect(wrapper.text()).toContain('第二页');
    expect(mocks.execute).toHaveBeenLastCalledWith('oa_available_seals', {
      formId: 42,
      params: { pageNo: 2, pageSize: 1, selectedCompanyId: 8 },
      processDefinitionId: 'definition-1',
    });
  });

  it('revalidates retained values against newly loaded rows', async () => {
    mocks.execute
      .mockReset()
      .mockResolvedValueOnce({
        rows: [{ id: 1, name: '原选项' }],
        total: 1,
        version: 1,
      })
      .mockResolvedValueOnce({
        rows: [{ id: 2, name: '新选项' }],
        total: 1,
        version: 1,
      });
    const companyId = ref(8);
    const { setValue, wrapper } = mountSelector({
      companyId,
      modelValue: [1],
      props: { onDependencyChange: 'keep-and-revalidate' },
    });
    await flushPromises();
    companyId.value = 9;
    await nextTick();
    await flushPromises();

    expect(wrapper.emitted('update:modelValue')).toContainEqual([[]]);
    expect(setValue).toHaveBeenCalledWith('keeperName', undefined);
  });

  it('maps a safe dotted result path', async () => {
    mocks.execute.mockReset().mockResolvedValue({
      rows: [{ id: 1, keeper: { name: '保管员甲' }, name: '公章' }],
      total: 1,
      version: 1,
    });
    const setValue = vi.fn();
    const { wrapper } = mountSelector({
      props: { outputMappings: { 'keeper.name': 'keeperName' } },
      setValue,
    });
    await flushPromises();
    wrapper.findComponent(SelectStub).vm.$emit('change', [1]);
    await nextTick();

    expect(setValue).toHaveBeenCalledWith('keeperName', ['保管员甲']);
  });

  it('debounces search and sends only the configured control parameter', async () => {
    vi.useFakeTimers();
    const { wrapper } = mountSelector({
      props: { searchParamName: 'keyword' },
    });
    try {
      await flushPromises();
      expect(mocks.execute).toHaveBeenLastCalledWith('oa_available_seals', {
        formId: 42,
        params: { keyword: '', selectedCompanyId: 8 },
        processDefinitionId: 'definition-1',
      });
      mocks.execute.mockClear();

      wrapper.findComponent(SelectStub).vm.$emit('search', '公');
      wrapper.findComponent(SelectStub).vm.$emit('search', '公章');
      await vi.advanceTimersByTimeAsync(299);
      expect(mocks.execute).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(1);
      await flushPromises();
      expect(mocks.execute).toHaveBeenCalledTimes(1);
      expect(mocks.execute).toHaveBeenCalledWith('oa_available_seals', {
        formId: 42,
        params: { keyword: '公章', selectedCompanyId: 8 },
        processDefinitionId: 'definition-1',
      });
    } finally {
      wrapper.unmount();
      vi.useRealTimers();
    }
  });

  it('shows a bounded error and retries the same trusted request', async () => {
    mocks.execute
      .mockReset()
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce({
        rows: [{ id: 2, name: '重试成功' }],
        total: 1,
        version: 1,
      });
    const { wrapper } = mountSelector({});
    await flushPromises();

    expect(wrapper.text()).toContain('网络暂时不可用，点击重试');
    await wrapper.find('.text-red-500').trigger('click');
    await flushPromises();

    expect(mocks.execute).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('重试成功');
  });

  it('renders persisted label snapshots in read-only history without executing', async () => {
    const { wrapper } = mountSelector({
      getValue: (field) =>
        field === 'sealSnapshot'
          ? [{ label: '历史公章', value: 99 }]
          : undefined,
      props: {
        runtimeContext: undefined,
        snapshotField: 'sealSnapshot',
      },
    });
    await flushPromises();

    expect(mocks.execute).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('历史公章');
  });

  it('persists label/value snapshots without copying server-only columns', async () => {
    mocks.execute.mockReset().mockResolvedValue({
      rows: [{ id: 1, name: '公章', secret: 'server-only' }],
      total: 1,
      version: 1,
    });
    const setValue = vi.fn();
    const { wrapper } = mountSelector({
      props: { snapshotField: 'sealSnapshot' },
      setValue,
    });
    await flushPromises();
    wrapper.findComponent(SelectStub).vm.$emit('change', [1]);
    await nextTick();

    expect(setValue).toHaveBeenCalledWith('sealSnapshot', [
      { label: '公章', value: 1 },
    ]);
  });
});

describe('remoteDataSourceSelect logic', () => {
  // ---------- buildExecuteParams ----------
  describe('buildExecuteParams', () => {
    const baseContext = {
      FORM: { companyId: 9, keyword: '公章' },
      PROCESS: { definitionKey: 'oa_seal_general', instanceId: 'pi-1' },
      USER: { companyId: 9, deptId: 3, id: 7 },
    };

    it('resolves FORM bindings to execute params (companyId=9)', () => {
      const result = buildExecuteParams(
        { companyId: 'FORM.companyId' },
        baseContext,
      );
      expect(result).toEqual({ companyId: 9 });
    });

    it('resolves USER bindings', () => {
      expect(buildExecuteParams({ userId: 'USER.id' }, baseContext)).toEqual({
        userId: 7,
      });
    });

    it('resolves multiple bindings in one call', () => {
      const result = buildExecuteParams(
        { companyId: 'FORM.companyId', keyword: 'FORM.keyword' },
        baseContext,
      );
      expect(result).toEqual({ companyId: 9, keyword: '公章' });
    });

    it('returns undefined for missing form fields', () => {
      const result = buildExecuteParams(
        { missing: 'FORM.nonexistent' },
        baseContext,
      );
      expect(result).toEqual({ missing: undefined });
    });

    it('rejects unsafe binding expressions', () => {
      expect(() =>
        buildExecuteParams({ bad: 'FORM.__proto__.polluted' }, baseContext),
      ).toThrow(/binding/i);
    });
  });

  // ---------- dependency change with clear-and-reload ----------
  describe('dependency change with clear-and-reload', () => {
    it('clears value=[1] and mapped fields on clear-and-reload', () => {
      const result = applyDependencyChange({
        currentValue: [1],
        multiple: true,
        outputMappings: { keeperName: 'keeperName' },
        strategy: 'clear-and-reload',
        valueField: 'id',
      });
      expect(result.value).toEqual([]);
      expect(result.mappedValues).toEqual({ keeperName: undefined });
      expect(result.reload).toBe(true);
    });

    it('clears single value on clear-and-reload', () => {
      const result = applyDependencyChange({
        currentValue: 1,
        multiple: false,
        outputMappings: { keeperName: 'keeperName' },
        strategy: 'clear-and-reload',
        valueField: 'id',
      });
      expect(result.value).toBeUndefined();
      expect(result.mappedValues).toEqual({ keeperName: undefined });
    });
  });

  // ---------- buildSelectionSnapshot ----------
  describe('buildSelectionSnapshot', () => {
    it('extracts label/value pairs from selected rows', () => {
      const rows = [
        { id: 1, keeperName: '张三', name: '公章' },
        { id: 2, keeperName: '李四', name: '财务章' },
      ];
      const snapshot = buildSelectionSnapshot(rows, 'name', 'id');
      expect(snapshot).toEqual([
        { label: '公章', value: 1 },
        { label: '财务章', value: 2 },
      ]);
    });

    it('returns empty array for no selections', () => {
      expect(buildSelectionSnapshot([], 'name', 'id')).toEqual([]);
    });

    it('writes only label/value, not the full server row', () => {
      const rows = [{ id: 1, keeperName: '张三', name: '公章', secret: 'x' }];
      const snapshot = buildSelectionSnapshot(rows, 'name', 'id');
      expect(snapshot).toEqual([{ label: '公章', value: 1 }]);
      expect(snapshot[0]).not.toHaveProperty('keeperName');
      expect(snapshot[0]).not.toHaveProperty('secret');
    });
  });

  // ---------- validateSelectorParamName ----------
  describe('validateSelectorParamName', () => {
    it('accepts valid parameter names', () => {
      expect(validateSelectorParamName('keyword')).toBe(true);
      expect(validateSelectorParamName('pageNo')).toBe(true);
      expect(validateSelectorParamName('page_size')).toBe(true);
      expect(validateSelectorParamName('_private')).toBe(true);
    });

    it('rejects reserved server parameters', () => {
      expect(validateSelectorParamName('tenantId')).toBe(false);
      expect(validateSelectorParamName('userId')).toBe(false);
      expect(validateSelectorParamName('deptId')).toBe(false);
      expect(validateSelectorParamName('companyId')).toBe(false);
    });

    it('rejects forbidden names', () => {
      expect(validateSelectorParamName('__proto__')).toBe(false);
      expect(validateSelectorParamName('constructor')).toBe(false);
      expect(validateSelectorParamName('prototype')).toBe(false);
    });

    it('rejects invalid schema names', () => {
      expect(validateSelectorParamName('')).toBe(false);
      expect(validateSelectorParamName('123abc')).toBe(false);
      expect(validateSelectorParamName('a'.repeat(64))).toBe(false);
      expect(validateSelectorParamName('a-b')).toBe(false);
    });
  });
});
