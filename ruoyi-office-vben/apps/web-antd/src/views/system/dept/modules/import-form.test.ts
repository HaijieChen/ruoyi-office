import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h, nextTick, ref } from 'vue';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import ImportForm from './import-form.vue';

const mocks = vi.hoisted(() => ({
  getDeptImportTemplate: vi.fn(),
  validateDeptImport: vi.fn(),
  importDept: vi.fn(),
  downloadFileFromBlobPart: vi.fn(),
  message: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
  modalConfirm: vi.fn(),
  modalClose: vi.fn(),
  modalLock: vi.fn(),
  modalUnlock: vi.fn(),
}));

vi.mock('#/api/system/dept', () => ({
  getDeptImportTemplate: mocks.getDeptImportTemplate,
  validateDeptImport: mocks.validateDeptImport,
  importDept: mocks.importDept,
}));

vi.mock('@vben/utils', () => ({
  downloadFileFromBlobPart: mocks.downloadFileFromBlobPart,
}));

vi.mock('@vben/common-ui', () => ({
  useVbenModal: (options: {
    onConfirm?: () => Promise<void> | void;
    onClosed?: () => void;
  }) => {
    const open = ref(true);
    const api = {
      open: () => {
        open.value = true;
      },
      close: async () => {
        mocks.modalClose();
        options.onClosed?.();
        open.value = false;
      },
      lock: mocks.modalLock,
      unlock: mocks.modalUnlock,
    };
    mocks.modalConfirm.mockImplementation(async () => {
      await options.onConfirm?.();
    });
    const Modal = defineComponent({
      name: 'MockModal',
      props: {
        confirmText: { type: String, default: '' },
        confirmDisabled: { type: Boolean, default: false },
        title: { type: String, default: '' },
      },
      setup(props, { slots, expose }) {
        expose(api);
        return () =>
          h('div', { 'data-testid': 'import-modal', 'data-title': props.title }, [
            slots.default?.(),
            slots['prepend-footer']?.(),
            h(
              'button',
              {
                type: 'button',
                'data-testid': 'import-modal-confirm',
                disabled: props.confirmDisabled,
                onClick: () => mocks.modalConfirm(),
              },
              props.confirmText || 'confirm',
            ),
          ]);
      },
    });
    return [Modal, api];
  },
}));

vi.mock('ant-design-vue', () => ({
  Alert: defineComponent({
    name: 'AAlert',
    props: ['message', 'description', 'type'],
    setup(props) {
      return () =>
        h('div', {
          'data-testid': 'alert',
          'data-message': props.message,
        });
    },
  }),
  Button: defineComponent({
    name: 'AButton',
    emits: ['click'],
    setup(_, { attrs, emit, slots }) {
      return () =>
        h(
          'button',
          {
            ...attrs,
            type: 'button',
            onClick: () => emit('click'),
          },
          slots.default?.(),
        );
    },
  }),
  Table: defineComponent({
    name: 'ATable',
    props: ['dataSource'],
    setup(props) {
      return () =>
        h('div', {
          'data-testid': 'dept-import-error-table',
          'data-rows': String(props.dataSource?.length ?? 0),
        });
    },
  }),
  Upload: defineComponent({
    name: 'AUpload',
    props: ['beforeUpload'],
    setup(props, { slots }) {
      return () =>
        h('div', { 'data-testid': 'upload' }, [
          slots.default?.(),
          h('button', {
            type: 'button',
            'data-testid': 'trigger-upload',
            onClick: () => {
              const file = new File(['pk'], 'org.xlsx', {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
              });
              props.beforeUpload?.(file);
            },
          }),
        ]);
    },
  }),
  message: mocks.message,
}));

beforeEach(() => {
  Object.values(mocks).forEach((value) => {
    if (typeof value === 'function') {
      value.mockReset();
    }
  });
  mocks.message.success.mockReset();
  mocks.message.warning.mockReset();
  mocks.message.error.mockReset();
  mocks.message.info.mockReset();
  mocks.getDeptImportTemplate.mockResolvedValue(new Blob(['tpl']));
  mocks.validateDeptImport.mockResolvedValue({
    fileDigest: 'digest-1',
    totalRows: 2,
    createCount: 2,
    skipCount: 0,
    canCommit: true,
    errors: [],
  });
  mocks.importDept.mockResolvedValue({
    fileDigest: 'digest-1',
    totalRows: 2,
    createCount: 2,
    skipCount: 0,
    canCommit: true,
    errors: [],
  });
});

describe('SystemDeptImportForm', () => {
  it('downloads template', async () => {
    const wrapper = mount(ImportForm);
    await wrapper.get('[data-testid="dept-import-download-template"]').trigger('click');
    await flushPromises();
    expect(mocks.getDeptImportTemplate).toHaveBeenCalled();
    expect(mocks.downloadFileFromBlobPart).toHaveBeenCalled();
  });

  it('warns when confirming without file', async () => {
    mount(ImportForm);
    await mocks.modalConfirm();
    expect(mocks.message.warning).toHaveBeenCalledWith('请先选择 .xlsx 文件');
    expect(mocks.validateDeptImport).not.toHaveBeenCalled();
  });

  it('validates then commits successfully', async () => {
    const wrapper = mount(ImportForm);
    const onSuccess = vi.fn();
    wrapper.vm.$emit = onSuccess as any;
    // select file
    await wrapper.get('[data-testid="trigger-upload"]').trigger('click');
    await nextTick();
    // first confirm → validate
    await mocks.modalConfirm();
    await flushPromises();
    expect(mocks.validateDeptImport).toHaveBeenCalled();
    expect(mocks.message.success).toHaveBeenCalled();
    // second confirm → import
    await mocks.modalConfirm();
    await flushPromises();
    expect(mocks.importDept).toHaveBeenCalledWith(expect.any(File), 'digest-1');
  });

  it('shows error table when validation fails', async () => {
    mocks.validateDeptImport.mockResolvedValueOnce({
      fileDigest: 'd2',
      totalRows: 1,
      createCount: 0,
      skipCount: 0,
      canCommit: false,
      errors: [
        {
          rowNumber: 2,
          orgPath: 'A',
          field: 'orgType',
          code: 'PARENT_TYPE_INVALID',
          message: '公司不能挂在部门下',
        },
      ],
    });
    const wrapper = mount(ImportForm);
    await wrapper.get('[data-testid="trigger-upload"]').trigger('click');
    await mocks.modalConfirm();
    await flushPromises();
    expect(wrapper.get('[data-testid="dept-import-error-table"]').attributes('data-rows')).toBe(
      '1',
    );
    expect(mocks.importDept).not.toHaveBeenCalled();
  });
});
