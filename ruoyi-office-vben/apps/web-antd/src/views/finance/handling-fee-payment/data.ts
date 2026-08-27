import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getSimpleCompanyList } from '#/api/system/dept';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'feeDate',
      label: '付款日期',
      component: 'RangePicker',
      componentProps: {
        allowClear: true,
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
        style: 'width:100%',
      },
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
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'feeDate',
      title: '付款日期',
      width: 120,
      formatter: 'formatDate',
    },
    {
      field: 'amount',
      title: '金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    { field: 'currency', title: '币种', width: 80 },
    { field: 'entityCompanyName', title: '主体公司', minWidth: 140 },
    { field: 'accountName', title: '户名', minWidth: 120 },
    { field: 'bankName', title: '开户行', minWidth: 140 },
    { field: 'accountNoMasked', title: '账号', minWidth: 160 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'actions',
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
