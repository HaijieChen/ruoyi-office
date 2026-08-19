import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'applicationNo',
      label: '单号',
      component: 'Input',
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '审批中', value: 'PENDING' },
          { label: '待支付', value: 'WAIT_PAY' },
          { label: '已支付', value: 'PAID' },
          { label: '已驳回', value: 'REJECTED' },
          { label: '已取消', value: 'CANCELLED' },
        ],
      },
    },
    {
      fieldName: 'payeeName',
      label: '收款方',
      component: 'Input',
    },
    {
      fieldName: 'entityCompanyDeptId',
      label: '主体公司',
      component: 'InputNumber',
      componentProps: {
        class: 'w-full',
        placeholder: '主体公司 deptId',
      },
    },
  ];
}

const STATUS_LABEL: Record<string, string> = {
  PENDING: '审批中',
  WAIT_PAY: '待支付',
  PAID: '已支付',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
};

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'applicationNo', title: '单号', minWidth: 150 },
    { field: 'processTitle', title: '标题', minWidth: 180 },
    {
      field: 'entityCompanyName',
      title: '主体公司',
      minWidth: 140,
      formatter: ({ cellValue }) => cellValue || '历史未记录',
    },
    { field: 'payeeName', title: '收款方', minWidth: 140 },
    { field: 'applyAmount', title: '金额', minWidth: 100 },
    { field: 'currency', title: '币种', width: 80 },
    { field: 'paymentReason', title: '事由', minWidth: 100 },
    {
      field: 'costProject',
      title: '产品名称',
      minWidth: 120,
      formatter: ({ cellValue }) =>
        getDictLabel(DICT_TYPE.FINANCE_PRODUCT_TYPE, cellValue) ||
        cellValue ||
        '-',
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      formatter: ({ cellValue }) => STATUS_LABEL[cellValue] || cellValue,
    },
    { field: 'currentNodeName', title: '当前节点', minWidth: 120 },
    { field: 'createTime', title: '创建时间', minWidth: 160 },
    {
      field: 'actions',
      title: '操作',
      width: 200,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
