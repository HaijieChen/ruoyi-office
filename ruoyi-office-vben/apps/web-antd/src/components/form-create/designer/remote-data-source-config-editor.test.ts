/* eslint-disable vue/one-component-per-file */
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import RemoteDataSourceConfigEditor from './remote-data-source-config-editor.vue';

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  getList: vi.fn(),
  getMetadata: vi.fn(),
  push: vi.fn(),
}));

vi.mock('ant-design-vue', () => ({
  Modal: { confirm: mocks.confirm },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}));

vi.mock('#/api/bpm/form-data-source', () => ({
  getDataSourceSimpleList: mocks.getList,
  getPublishedDataSourceMetadata: mocks.getMetadata,
}));

const SelectStub = defineComponent({
  name: 'ASelect',
  inheritAttrs: false,
  props: ['disabled', 'options', 'value'],
  emits: ['change', 'update:modelValue'],
  setup(props, { attrs, emit }) {
    return () =>
      h(
        'button',
        {
          ...attrs,
          'data-current-value': JSON.stringify(props.value),
          disabled: props.disabled,
          onClick: () => {
            const value = (attrs['data-next-value'] as string) || '';
            emit('update:modelValue', value);
            emit('change', value);
          },
          type: 'button',
        },
        JSON.stringify(props.options ?? []),
      );
  },
});

const ButtonStub = defineComponent({
  name: 'AButton',
  inheritAttrs: false,
  props: ['disabled'],
  emits: ['click'],
  setup(props, { attrs, emit, slots }) {
    return () =>
      h(
        'button',
        {
          ...attrs,
          disabled: props.disabled,
          onClick: () => emit('click'),
          type: 'button',
        },
        slots.default?.(),
      );
  },
});

const PlainStub = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () =>
      h(
        'div',
        attrs,
        Object.values(slots).flatMap((slot) => slot?.() ?? []),
      );
  },
});

const metadata = (code = 'crm_customers', version = 1) => ({
  code,
  id: version,
  labelField: 'name',
  name: code === 'crm_customers' ? '客商' : '供应商',
  pageable: true,
  parameterFields: [
    { label: '当前租户', name: 'tenantId', required: true, type: 'LONG' },
    { label: '关键词', name: 'keyword', type: 'STRING' },
    { label: '主体公司', name: 'company', required: true, type: 'LONG' },
  ],
  publishedVersion: version,
  resultFields: [
    { label: '编号', name: 'id', type: 'LONG' },
    { label: '名称', name: 'name', type: 'STRING' },
  ],
  type: 1,
  valueField: 'id',
});

let version = 0;

function mountEditor(options: {
  activeProps?: Record<string, any>;
  formRules?: any[];
  modelValue?: string;
} = {}) {
  version += 1;
  const activeRule = {
    props: {
      dataSourceCode: options.modelValue ?? 'crm_customers',
      labelField: 'name',
      outputMappings: {},
      parameterBindings: {},
      valueField: 'id',
      ...options.activeProps,
    },
  };
  if (!mocks.getList.getMockImplementation()) {
    mocks.getList.mockResolvedValue([
      {
        code: 'crm_customers',
        id: 1,
        name: '客商',
        publishedVersion: version,
        status: 0,
        type: 1,
      },
      {
        code: 'crm_suppliers',
        id: 2,
        name: '供应商',
        publishedVersion: version,
        status: 0,
        type: 1,
      },
    ]);
  }
  if (!mocks.getMetadata.getMockImplementation()) {
    mocks.getMetadata.mockImplementation((code: string) =>
      Promise.resolve(metadata(code, version)),
    );
  }
  const wrapper = mount(RemoteDataSourceConfigEditor, {
    global: {
      stubs: {
        AAlert: PlainStub,
        AButton: ButtonStub,
        AInput: true,
        AInputNumber: PlainStub,
        ASelect: SelectStub,
        ASwitch: PlainStub,
        ATag: PlainStub,
      },
    },
    props: {
      getActiveRule: () => activeRule,
      getFormRules: () =>
        options.formRules ?? [
          { field: 'companyId', title: '主体公司', type: 'select' },
          { field: 'customerName', title: '客商名称', type: 'input' },
        ],
      modelValue: options.modelValue ?? 'crm_customers',
    },
  });
  return { activeRule, wrapper };
}

beforeEach(() => {
  mocks.confirm.mockReset();
  mocks.getList.mockReset();
  mocks.getMetadata.mockReset();
  mocks.push.mockReset();
});

describe('RemoteDataSourceConfigEditor', () => {
  it('binds saved values through the Ant Design Vue value prop', async () => {
    const { wrapper } = mountEditor();
    await flushPromises();

    expect(
      wrapper.get('[data-testid="source-select"]').attributes('data-current-value'),
    ).toBe('"crm_customers"');
  });

  it('offers other remote selectors as dependency fields', async () => {
    const { wrapper } = mountEditor({
      formRules: [
        {
          field: 'companyId',
          title: '主体公司',
          type: 'RemoteDataSourceSelect',
        },
        { field: 'customerName', title: '客商名称', type: 'input' },
      ],
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="dependency-select"]').text()).toContain(
      '"value":"companyId"',
    );
  });

  it('uses strict selects and labels missing historical values explicitly', async () => {
    const { wrapper } = mountEditor({
      activeProps: { labelField: 'deletedField' },
    });
    await flushPromises();

    expect(wrapper.findComponent({ name: 'AInput' }).exists()).toBe(false);
    expect(wrapper.findAll('[data-schema-select]').length).toBeGreaterThan(2);
    expect(wrapper.text()).toContain('当前租户');
    expect(wrapper.text()).toContain('系统自动注入');
    expect(wrapper.text()).toContain('字段已不存在（deletedField）');
  });

  it('keeps old props on load failure, disables adding rows and supports retry', async () => {
    mocks.getList.mockResolvedValue([
      {
        code: 'crm_customers',
        id: 1,
        name: '客商',
        publishedVersion: ++version,
        status: 0,
        type: 1,
      },
    ]);
    mocks.getMetadata
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(metadata('crm_customers', version));
    const { activeRule, wrapper } = mountEditor({
      activeProps: { parameterBindings: { company: 'FORM.companyId' } },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('元数据加载失败');
    expect(wrapper.get('[data-testid="add-parameter"]').attributes('disabled')).toBeDefined();
    expect(activeRule.props.parameterBindings).toEqual({
      company: 'FORM.companyId',
    });

    await wrapper.get('[data-testid="retry"]').trigger('click');
    await flushPromises();
    expect(mocks.getMetadata).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).not.toContain('元数据加载失败');
  });

  it('confirms a source switch and applies reconciled props only after approval', async () => {
    const { activeRule, wrapper } = mountEditor({
      activeProps: {
        labelField: 'deletedField',
        outputMappings: { deletedResult: 'customerName', name: 'customerName' },
        parameterBindings: {
          company: 'FORM.companyId',
          deletedParameter: 'FORM.companyId',
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="source-select"]').trigger('click');
    expect(mocks.confirm).toHaveBeenCalledTimes(1);
    expect(activeRule.props.dataSourceCode).toBe('crm_customers');

    await mocks.confirm.mock.calls[0]![0].onOk();
    await flushPromises();
    expect(activeRule.props.dataSourceCode).toBe('crm_suppliers');
    expect(activeRule.props.labelField).toBe('name');
    expect(activeRule.props.outputMappings).toEqual({ name: 'customerName' });
    expect(activeRule.props.parameterBindings).toEqual({
      company: 'FORM.companyId',
    });
  });

  it('refreshes without cache and opens data source management', async () => {
    const { wrapper } = mountEditor();
    await flushPromises();
    expect(mocks.getMetadata).toHaveBeenCalledTimes(1);

    await wrapper.get('[data-testid="refresh"]').trigger('click');
    await flushPromises();
    expect(mocks.getMetadata).toHaveBeenCalledTimes(2);

    await wrapper.get('[data-testid="manage"]').trigger('click');
    expect(mocks.push).toHaveBeenCalledWith({ name: 'BpmFormDataSource' });
  });

  it('shows specific empty states when no result or form fields are available', async () => {
    const { wrapper } = mountEditor({ formRules: [] });
    mocks.getMetadata.mockResolvedValue({
      ...metadata('crm_customers', version),
      resultFields: [],
    });
    await wrapper.get('[data-testid="refresh"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('数据源没有可用返回字段');
    expect(wrapper.text()).toContain('当前表单没有可映射的普通字段');
    expect(wrapper.text()).not.toContain('暂无数据');
  });
});
