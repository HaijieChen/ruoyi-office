import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import FormBody from '../modules/form-body.vue';

const mocks = vi.hoisted(() => ({
  getSimpleDeptList: vi.fn(),
  getSimpleUserList: vi.fn(),
  httpRequest: vi.fn(),
  messageError: vi.fn(),
  ocrExpenseInvoice: vi.fn(),
}));

vi.mock('ant-design-vue', () => {
  const stub = { template: '<div><slot /></div>' };
  const form = { ...stub, Item: stub };
  return {
    Button: stub,
    DatePicker: stub,
    Form: form,
    Input: stub,
    InputNumber: stub,
    Select: {
      name: 'SelectStub',
      props: ['placeholder', 'value'],
      template: '<div />',
    },
    Switch: stub,
    message: {
      error: mocks.messageError,
      loading: () => vi.fn(),
      success: vi.fn(),
      warning: vi.fn(),
    },
  };
});

vi.mock('@vben/hooks', () => ({ getDictOptions: () => [] }));
vi.mock('@vben/stores', () => ({
  useUserStore: () => ({
    userInfo: { deptId: 10, deptName: '测试部门', id: 1, nickname: '测试用户' },
  }),
}));
vi.mock('#/api/bpm/oa/outing', () => ({
  getOutingPage: () => Promise.resolve({ list: [] }),
}));
vi.mock('#/api/bpm/oa/trip', () => ({
  getTripPage: () => Promise.resolve({ list: [] }),
}));
vi.mock('#/api/system/dept', () => ({
  getSimpleDeptList: mocks.getSimpleDeptList,
}));
vi.mock('#/api/system/user', () => ({
  getSimpleUserList: mocks.getSimpleUserList,
}));
vi.mock('#/api/hrm/employee', () => ({
  getEmployeeWageCardByUserId: () => Promise.resolve(null),
}));
vi.mock('#/api/finance/expense-reimbursement', () => ({
  createExpenseReimbursement: vi.fn(),
  getOccupiedPredocIds: () => Promise.resolve([]),
  ocrExpenseInvoice: mocks.ocrExpenseInvoice,
}));
vi.mock('#/components/upload/use-upload', () => ({
  useUpload: () => ({ httpRequest: mocks.httpRequest }),
}));
vi.mock('#/components/upload', () => ({
  FileUpload: {
    name: 'FileUploadStub',
    props: ['api', 'value'],
    template: '<div />',
  },
}));
vi.mock('../modules/predoc-overlay.vue', () => ({
  default: { template: '<div />' },
}));

beforeEach(() => {
  mocks.getSimpleDeptList
    .mockReset()
    .mockResolvedValue([{ id: 10, name: '报销主体有限公司', orgType: '1' }]);
  mocks.getSimpleUserList
    .mockReset()
    .mockResolvedValue([
      { deptId: 10, deptName: '测试部门', id: 1, nickname: '测试用户' },
    ]);
  mocks.httpRequest.mockReset();
  mocks.messageError.mockReset();
  mocks.ocrExpenseInvoice.mockReset().mockResolvedValue({
    buyerName: '其它主体有限公司',
    invoiceNo: '26300000000000000001',
  });
});

describe('expense invoice buyer upload guard', () => {
  it('rejects the file before upload when invoice buyer differs from reimbursement company', async () => {
    const wrapper = mount(FormBody);
    await flushPromises();

    const categorySelect = wrapper
      .findAllComponents({ name: 'SelectStub' })
      .find((select) => select.props('placeholder') === '费用类型');
    if (!categorySelect) throw new Error('missing expense category select');
    categorySelect.vm.$emit('update:value', 'other');
    await nextTick();

    const upload = wrapper.findComponent({ name: 'FileUploadStub' });
    expect(upload.exists()).toBe(true);
    const api = upload.props('api') as (file: File) => Promise<unknown>;

    await expect(api(new File(['invoice'], 'invoice.jpg'))).rejects.toThrow(
      'buyer mismatch',
    );
    expect(mocks.httpRequest).not.toHaveBeenCalled();
    expect(mocks.messageError).toHaveBeenCalledWith(
      '发票主体与报销主体不一致，不允许上传（发票主体：其它主体有限公司，报销主体：报销主体有限公司）',
    );

    wrapper.unmount();
  });

  it.each([
    ['matches the reimbursement company', '报销主体有限公司'],
    ['cannot be identified', undefined],
  ])(
    'continues uploading when the invoice buyer %s',
    async (_case, buyerName) => {
      mocks.ocrExpenseInvoice.mockResolvedValue({
        buyerName,
        invoiceNo: '26300000000000000001',
      });
      mocks.httpRequest.mockResolvedValue('https://files.test/invoice.jpg');
      const wrapper = mount(FormBody);
      await flushPromises();

      const categorySelect = wrapper
        .findAllComponents({ name: 'SelectStub' })
        .find((select) => select.props('placeholder') === '费用类型');
      if (!categorySelect) throw new Error('missing expense category select');
      categorySelect.vm.$emit('update:value', 'other');
      await nextTick();

      const api = wrapper
        .findComponent({ name: 'FileUploadStub' })
        .props('api') as (file: File) => Promise<unknown>;
      await expect(api(new File(['invoice'], 'invoice.jpg'))).resolves.toBe(
        'https://files.test/invoice.jpg',
      );
      expect(mocks.httpRequest).toHaveBeenCalledOnce();
      expect(mocks.messageError).not.toHaveBeenCalled();

      wrapper.unmount();
    },
  );
});
