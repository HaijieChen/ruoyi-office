import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'productType',
      label: '产品类型',
      component: 'Input',
    },
    {
      fieldName: 'uninvoicedOnly',
      label: '只看未开票',
      component: 'Switch',
    },
    {
      fieldName: 'invoicedArOnly',
      label: '只看已开票应收',
      component: 'Switch',
    },
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'orderNo', title: '商务单号', minWidth: 140 },
    { field: 'entityCompanyName', title: '主体公司', minWidth: 140 },
    { field: 'productType', title: '产品类型', minWidth: 120 },
    { field: 'settlementAmount', title: '结算金额', minWidth: 110 },
    { field: 'invoicedOccupiedAmount', title: '已开票', minWidth: 100 },
    { field: 'confirmedClaimedAmount', title: '已认款', minWidth: 100 },
    { field: 'uninvoicedAmount', title: '未开票应收', minWidth: 120 },
    { field: 'invoicedArAmount', title: '已开票应收', minWidth: 120 },
    { field: 'arTotalAmount', title: '应收合计', minWidth: 110 },
    { field: 'currency', title: '币种', width: 80 },
  ];
}
