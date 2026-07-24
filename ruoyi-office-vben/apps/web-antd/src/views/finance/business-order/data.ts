import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

/** 列表搜索表单（对应工作簿字段） */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'orderNo',
      label: '订单编号',
      component: 'Input',
      componentProps: { placeholder: '请输入订单编号', allowClear: true },
    },
    {
      fieldName: 'bankAccount',
      label: '银行账号',
      component: 'Input',
      componentProps: { placeholder: '请输入银行账号', allowClear: true },
    },
    {
      fieldName: 'importDate',
      label: '导入日期',
      component: 'DatePicker',
      componentProps: { allowClear: true, style: 'width:100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      fieldName: 'productName',
      label: '产品/服务',
      component: 'Input',
      componentProps: { placeholder: '请输入产品或服务名称', allowClear: true },
    },
    {
      fieldName: 'contactPerson',
      label: '联系人',
      component: 'Input',
      componentProps: { placeholder: '请输入联系人', allowClear: true },
    },
    {
      fieldName: 'contractProcessId',
      label: '合同流程ID',
      component: 'Input',
      componentProps: { placeholder: '请输入合同流程ID（可选）', allowClear: true },
    },
  ];
}

/** 列表列定义（对应工作簿字段） */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'orderNo', title: '订单编号', width: 180, fixed: 'left' },
    { field: 'importDate', title: '导入日期', width: 120, formatter: 'formatDate' },
    { field: 'importerName', title: '导入人', width: 100 },
    { field: 'bankAccount', title: '银行账号', minWidth: 160 },
    { field: 'contractProcessId', title: '合同流程ID', minWidth: 140 },
    { field: 'orderDate', title: '签单日期', width: 120, formatter: 'formatDate' },
    { field: 'productName', title: '产品/服务', minWidth: 150 },
    { field: 'contactPerson', title: '联系人', width: 100 },
    { field: 'executionStartDate', title: '执行开始', width: 120, formatter: 'formatDate' },
    { field: 'executionEndDate', title: '执行结束', width: 120, formatter: 'formatDate' },
    { field: 'payerName', title: '付款方', minWidth: 120 },
    {
      field: 'signedExecutionAmount',
      title: '签约执行金额',
      width: 140,
      formatter: 'formatAmount2',
    },
    {
      field: 'discountRate',
      title: '折扣',
      width: 110,
      formatter: 'formatAmount2',
    },
    {
      field: 'settlementAmount',
      title: '结算金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'confirmedClaimedAmount',
      title: '已确认到款',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'remainingBalance',
      title: '剩余余额',
      width: 120,
      formatter: 'formatAmount2',
      fixed: 'right',
    },
    { field: 'remark', title: '备注', minWidth: 120 },
    { field: 'createTime', title: '创建时间', width: 160, formatter: 'formatDateTime' },
  ];
}
