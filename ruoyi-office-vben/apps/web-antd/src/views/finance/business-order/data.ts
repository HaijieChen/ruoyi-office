import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getSimpleCompanyList } from '#/api/system/dept';
import { financeProductLabel } from '#/views/finance/shared/display-labels';

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
      fieldName: 'entityCompanyDeptId',
      label: '主体公司',
      component: 'ApiSelect',
      componentProps: {
        placeholder: '请选择主体公司',
        allowClear: true,
        showSearch: true,
        api: getSimpleCompanyList,
        labelField: 'name',
        valueField: 'id',
      },
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
      fieldName: 'contractApplicationNo',
      label: '合同业务单号',
      component: 'Input',
      componentProps: { placeholder: '请输入合同业务单号', allowClear: true },
    },
  ];
}

/** 列表列定义（对应工作簿字段） */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'orderNo', title: '订单编号', width: 180, fixed: 'left' },
    { field: 'importDate', title: '导入日期', width: 120, formatter: 'formatDate' },
    { field: 'importerName', title: '导入人', width: 100 },
    { field: 'entityCompanyName', title: '主体公司', minWidth: 160 },
    { field: 'contractApplicationNo', title: '合同业务单号', minWidth: 160 },
    { field: 'orderDate', title: '签单日期', width: 120, formatter: 'formatDate' },
    {
      field: 'productType',
      title: '产品/服务',
      minWidth: 150,
      // EXP-70：优先规范 productType，回退 legacy productName
      formatter: ({ row }: { row: { productName?: string; productType?: string } }) =>
        financeProductLabel(row.productType || row.productName),
    },
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
    },
    { field: 'remark', title: '备注', minWidth: 120 },
    { field: 'createTime', title: '创建时间', width: 160, formatter: 'formatDateTime' },
    {
      field: 'action',
      title: '操作',
      width: 140,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ];
}
