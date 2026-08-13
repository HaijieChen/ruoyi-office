import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'entityCompanyDeptId',
      label: '主体公司',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: async () => {
          const { getSimpleDeptList } = await import('#/api/system/dept');
          const list = await getSimpleDeptList();
          return (list || [])
            .filter((d: any) => String(d.orgType) === '1')
            .map((d: any) => ({ label: d.name, value: d.id }));
        },
        placeholder: '组织架构公司',
      },
    },
    {
      fieldName: 'accountName',
      label: '账户名称',
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
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'entityCompanyName', title: '主体公司', minWidth: 140 },
    { field: 'accountName', title: '账户名称', minWidth: 120 },
    { field: 'bankName', title: '开户行', minWidth: 140 },
    { field: 'accountHolder', title: '户名', minWidth: 120 },
    {
      field: 'accountNo',
      title: '账号',
      minWidth: 160,
      formatter: ({ row }) => row.accountNo || row.accountNoMasked || '',
    },
    { field: 'currency', title: '币种', width: 80 },
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
