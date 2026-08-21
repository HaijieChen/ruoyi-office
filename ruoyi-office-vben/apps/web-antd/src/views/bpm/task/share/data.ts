import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'processInstanceName',
      label: '流程名称',
      component: 'Input',
      componentProps: { placeholder: '请输入流程名称', allowClear: true },
    },
    {
      fieldName: 'createTime',
      label: '分享时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'processInstanceName', title: '流程名称', minWidth: 200 },
    { field: 'startUserId', title: '流程发起人', minWidth: 120 },
    {
      field: 'createTime',
      title: '分享时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
