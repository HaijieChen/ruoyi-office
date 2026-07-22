import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

/** 业务订单状态选项 */
export const ORDER_STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已关闭', value: 2 },
];

function orderStatusLabel(status: number): string {
  return ORDER_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? String(status);
}

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'orderNo',
      label: '订单编号',
      component: 'Input',
      componentProps: { placeholder: '请输入订单编号', allowClear: true },
    },
    {
      fieldName: 'businessSubject',
      label: '业务主体',
      component: 'Input',
      componentProps: { placeholder: '请输入业务主体', allowClear: true },
    },
    {
      fieldName: 'businessType',
      label: '业务类型',
      component: 'Input',
      componentProps: { placeholder: '请输入业务类型', allowClear: true },
    },
    {
      fieldName: 'contractRef',
      label: '合同编号',
      component: 'Input',
      componentProps: { placeholder: '请输入合同编号', allowClear: true },
    },
    {
      fieldName: 'projectRef',
      label: '项目编号',
      component: 'Input',
      componentProps: { placeholder: '请输入项目编号', allowClear: true },
    },
    {
      fieldName: 'currency',
      label: '币种',
      component: 'Input',
      componentProps: { placeholder: '如 CNY / USD', allowClear: true },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: ORDER_STATUS_OPTIONS,
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

/** 列表列定义 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'orderNo', title: '订单编号', width: 180, fixed: 'left' },
    { field: 'businessSubject', title: '业务主体', minWidth: 150 },
    { field: 'businessType', title: '业务类型', width: 120 },
    { field: 'contractRef', title: '合同编号', minWidth: 140 },
    { field: 'projectRef', title: '项目编号', minWidth: 140 },
    {
      field: 'receivableAmount',
      title: '应收金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'payableAmount',
      title: '应付金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    { field: 'currency', title: '币种', width: 80 },
    { field: 'ownerName', title: '负责人', width: 100 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      fixed: 'right',
      formatter: ({ cellValue }: { cellValue: number }) => orderStatusLabel(cellValue),
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 160,
      formatter: 'formatDateTime',
    },
  ];
}
