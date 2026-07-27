import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

/** 认领状态标签 */
const CLAIM_STATUS_OPTIONS = [
  { label: '未认领', value: 0 },
  { label: '部分认领', value: 1 },
  { label: '完全认领', value: 2 },
  { label: '已关闭', value: 3 },
];

function claimStatusLabel(status: number): string {
  return CLAIM_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? String(status);
}

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'receiptNo',
      label: '回单编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入回单编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'bankAccount',
      label: '银行账号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入银行账号',
        allowClear: true,
      },
    },
    {
      fieldName: 'payerName',
      label: '付款方名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入付款方名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'payerAccount',
      label: '付款方账号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入付款方账号',
        allowClear: true,
      },
    },
    {
      fieldName: 'bankSerialNo',
      label: '银行流水号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入银行流水号',
        allowClear: true,
      },
    },
    {
      fieldName: 'transactionDate',
      label: '交易日期',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
    {
      fieldName: 'importDate',
      label: '导入日期',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/** 列表列定义 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'receiptNo',
      title: '回单编号',
      width: 180,
      fixed: 'left',
    },
    {
      field: 'bankAccount',
      title: '银行账号',
      minWidth: 160,
    },
    {
      field: 'transactionDate',
      title: '交易日期',
      width: 120,
      formatter: 'formatDate',
    },
    {
      field: 'payerName',
      title: '付款方名称',
      minWidth: 150,
    },
    {
      field: 'payerAccount',
      title: '付款方账号',
      minWidth: 160,
    },
    {
      field: 'transactionAmount',
      title: '交易金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'claimedAmount',
      title: '已认领金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'unclaimedAmount',
      title: '未认领金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'summary',
      title: '摘要',
      minWidth: 200,
    },
    {
      field: 'bankSerialNo',
      title: '银行流水号',
      minWidth: 180,
    },
    {
      field: 'importDate',
      title: '导入日期',
      width: 120,
      formatter: 'formatDate',
    },
    {
      field: 'claimStatus',
      title: '认领状态',
      width: 100,
      fixed: 'right',
      formatter: ({ cellValue }: { cellValue: number }) =>
        claimStatusLabel(cellValue),
    },
  ];
}
