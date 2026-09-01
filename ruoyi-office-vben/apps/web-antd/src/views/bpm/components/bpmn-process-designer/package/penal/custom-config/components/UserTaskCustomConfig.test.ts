import { flushPromises, mount } from '@vue/test-utils';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import UserTaskCustomConfig from './UserTaskCustomConfig.vue';

const mocks = vi.hoisted(() => ({
  getSimpleUserList: vi.fn(),
  updateProperties: vi.fn(),
}));

vi.mock('ant-design-vue', () => {
  const stub = { template: '<div><slot /></div>' };
  const form = { ...stub, Item: stub };
  return {
    Button: stub,
    Divider: stub,
    Form: form,
    Input: stub,
    Radio: stub,
    RadioGroup: stub,
    Select: stub,
    SelectOption: stub,
    Switch: stub,
  };
});

vi.mock('@vben/icons', () => ({
  IconifyIcon: { template: '<span />' },
}));

vi.mock('#/api/system/user', () => ({
  getSimpleUserList: mocks.getSimpleUserList,
}));

vi.mock('#/views/bpm/components/simple-process-design/helpers', () => ({
  useFormFieldsPermission: () => ({
    fieldsPermissionConfig: { value: [] },
    formType: { value: 20 },
    getNodeConfigFormFields: vi.fn(),
  }),
}));

function extension($type: string, value: unknown) {
  return { $type, value };
}

function installBpmnInstances() {
  const values = [
    extension('flowable:ApproveType', 2),
    extension('flowable:AssignStartUserHandlerType', 2),
    extension('flowable:RejectHandlerType', 2),
    extension('flowable:RejectReturnTaskId', 'taskPrevious'),
    extension('flowable:AssignEmptyHandlerType', 3),
    extension('flowable:AssignEmptyUserIds', '224,225'),
  ];
  const bpmnElement = {
    businessObject: { extensionElements: { values } },
    id: 'taskFinance',
  };
  (window as any).bpmnInstances = {
    bpmnElement,
    modeler: {
      get: () => ({
        filter: () => [],
        get: () => ({ businessObject: {}, type: 'bpmn:UserTask' }),
      }),
    },
    modeling: { updateProperties: mocks.updateProperties },
    moddle: {
      create: ($type: string, properties: Record<string, unknown>) => ({
        $type,
        ...properties,
      }),
    },
  };
}

beforeEach(() => {
  mocks.getSimpleUserList.mockReset().mockResolvedValue([]);
  mocks.updateProperties.mockReset();
  installBpmnInstances();
});

describe('user task custom config', () => {
  it('keeps persisted extension values when opening an existing task', async () => {
    const wrapper = mount(UserTaskCustomConfig, {
      global: { provide: { prefix: 'flowable' } },
      props: { id: 'taskFinance', type: 'bpmn:UserTask' },
    });
    await flushPromises();

    const lastCall = mocks.updateProperties.mock.calls.at(-1);
    const savedValues = lastCall?.[1]?.extensionElements?.values;
    const valueOf = (type: string) =>
      savedValues.find((item: any) => item.$type === `flowable:${type}`)?.value;

    expect(valueOf('ApproveType')).toBe(2);
    expect(valueOf('AssignStartUserHandlerType')).toBe(2);
    expect(valueOf('RejectHandlerType')).toBe(2);
    expect(valueOf('RejectReturnTaskId')).toBe('taskPrevious');
    expect(valueOf('AssignEmptyHandlerType')).toBe(3);
    expect(valueOf('AssignEmptyUserIds')).toBe('224,225');

    wrapper.unmount();
  });
});
