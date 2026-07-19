import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import OrgChart from './org-chart.vue';

const mocks = vi.hoisted(() => ({
  getDeptList: vi.fn(),
  getSimpleUserList: vi.fn(),
  renderEcharts: vi.fn(),
}));

vi.mock('#/api/system/dept', () => ({
  getDeptList: mocks.getDeptList,
}));

vi.mock('#/api/system/user', () => ({
  getSimpleUserList: mocks.getSimpleUserList,
}));

vi.mock('@vben/plugins/echarts', () => ({
  EchartsUI: defineComponent({ name: 'EchartsUI' }),
  useEcharts: () => ({ renderEcharts: mocks.renderEcharts }),
}));

vi.mock('@vben/common-ui', () => ({
  Page: defineComponent({
    name: 'Page',
    setup(_, { slots }) {
      return () => h('div', [slots.extra?.(), slots.default?.()]);
    },
  }),
}));

vi.mock('@vben/icons', () => ({
  IconifyIcon: defineComponent({ name: 'IconifyIcon' }),
}));

vi.mock('ant-design-vue', () => ({
  Button: defineComponent({
    name: 'AButton',
    emits: ['click'],
    setup(_, { attrs, emit, slots }) {
      return () =>
        h(
          'button',
          { ...attrs, onClick: () => emit('click'), type: 'button' },
          slots.default?.(),
        );
    },
  }),
  Empty: defineComponent({
    name: 'AEmpty',
    setup() {
      return () => h('div');
    },
  }),
  Select: defineComponent({ name: 'ASelect' }),
  Spin: defineComponent({
    name: 'ASpin',
    setup(_, { slots }) {
      return () => h('div', slots.default?.());
    },
  }),
  message: { error: vi.fn(), warning: vi.fn() },
}));

const SelectStub = defineComponent({
  name: 'ASelect',
  inheritAttrs: false,
  props: ['options', 'value'],
  emits: ['change'],
  setup(props, { attrs, emit }) {
    return () =>
      h(
        'button',
        {
          ...attrs,
          'data-current-value': String(props.value ?? ''),
          onClick: () => emit('change', props.options?.[1]?.value),
          type: 'button',
        },
        JSON.stringify(props.options ?? []),
      );
  },
});

beforeEach(() => {
  mocks.getDeptList.mockReset();
  mocks.getSimpleUserList.mockReset();
  mocks.renderEcharts.mockReset();
  mocks.getDeptList.mockResolvedValue([
    {
      id: 100,
      leaderUserId: 1,
      name: '总公司',
      orgType: '1',
      parentId: 0,
      sort: 0,
      status: 0,
    },
    {
      id: 101,
      leaderUserId: 2,
      name: '公司1',
      orgType: '1',
      parentId: 0,
      sort: 1,
      status: 0,
    },
  ]);
  mocks.getSimpleUserList.mockResolvedValue([]);
});

describe('SystemDeptOrgChart', () => {
  it('lists every top-level company and renders the selected company tree', async () => {
    const wrapper = mount(OrgChart, {
      global: {
        stubs: {
          ASelect: SelectStub,
          EchartsUI: true,
        },
      },
    });
    await flushPromises();

    const selector = wrapper.get('[data-testid="company-select"]');
    expect(selector.text()).toContain('总公司');
    expect(selector.text()).toContain('公司1');

    await selector.trigger('click');
    await flushPromises();

    const latestOption = mocks.renderEcharts.mock.calls.at(-1)?.[0];
    expect(latestOption.series[0].data[0].name).toBe('公司1');
  });

  it('keeps the selected company after refreshing the organization data', async () => {
    const wrapper = mount(OrgChart, {
      global: {
        stubs: {
          ASelect: SelectStub,
          EchartsUI: true,
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="company-select"]').trigger('click');
    await flushPromises();
    mocks.getDeptList.mockResolvedValueOnce([
      {
        id: 101,
        name: '公司1',
        orgType: '1',
        parentId: 0,
        sort: 0,
        status: 0,
      },
      {
        id: 100,
        name: '总公司',
        orgType: '1',
        parentId: 0,
        sort: 1,
        status: 0,
      },
    ]);

    await wrapper.get('[data-testid="org-chart-refresh"]').trigger('click');
    await flushPromises();

    expect(
      wrapper
        .get('[data-testid="company-select"]')
        .attributes('data-current-value'),
    ).toBe('101');
    const latestOption = mocks.renderEcharts.mock.calls.at(-1)?.[0];
    expect(latestOption.series[0].data[0].name).toBe('公司1');
  });

  it('finishes loading before the asynchronous chart render resolves', async () => {
    mocks.renderEcharts.mockImplementationOnce(
      () => new Promise(() => undefined),
    );
    const wrapper = mount(OrgChart, {
      global: {
        stubs: {
          ASelect: SelectStub,
          EchartsUI: true,
        },
      },
    });
    await flushPromises();

    expect(wrapper.find('.chart-wrapper').exists()).toBe(true);
  });
});
