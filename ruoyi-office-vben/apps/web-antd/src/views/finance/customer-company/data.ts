import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '名称',
      component: 'Input',
    },
    {
      fieldName: 'taxNo',
      label: '税号',
      component: 'Input',
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '启用', value: 0 },
          { label: '停用', value: 1 },
        ],
      },
    },
    {
      fieldName: 'isCustomer',
      label: '客户角色',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
      },
    },
    {
      fieldName: 'isSupplier',
      label: '供应商角色',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
      },
    },
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'code', title: '编码', minWidth: 140 },
    { field: 'name', title: '名称', minWidth: 160 },
    { field: 'taxNo', title: '纳税人识别号', minWidth: 160 },
    {
      field: 'isCustomer',
      title: '客户',
      width: 70,
      formatter: ({ cellValue }) => (cellValue === false ? '否' : '是'),
    },
    {
      field: 'isSupplier',
      title: '供应商',
      width: 80,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    { field: 'bankName', title: '开户银行', minWidth: 120 },
    { field: 'bankAccount', title: '银行账号', minWidth: 140 },
    { field: 'address', title: '地址', minWidth: 140 },
    { field: 'phone', title: '电话', minWidth: 110 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 1 ? '停用' : '启用'),
    },
    {
      field: 'actions',
      title: '操作',
      width: 180,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
