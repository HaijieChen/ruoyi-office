<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getBankBalanceList } from '#/api/finance/report/bank-balance';

defineOptions({ name: 'FinanceBankBalanceReport' });

const excludedFxCount = ref(0);
const unmatchedReceiptCount = ref(0);

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [{ fieldName: 'asOf', label: '截止日期', component: 'DatePicker' }],
  },
  gridOptions: {
    columns: [
      { field: 'accountName', title: '账户', minWidth: 140 },
      { field: 'accountNoMasked', title: '账号', minWidth: 120 },
      { field: 'openingAmount', title: '期初', minWidth: 110 },
      { field: 'incomeAmount', title: '收入', minWidth: 110 },
      { field: 'payExpenseAmount', title: '付款支出', minWidth: 110 },
      { field: 'reimbursementExpenseAmount', title: '报销支出', minWidth: 110 },
      { field: 'balanceAmount', title: '余额', minWidth: 110 },
      { field: 'asOf', title: '截止日期', minWidth: 120 },
    ],
    height: 'auto',
    pagerConfig: { enabled: false },
    proxyConfig: {
      ajax: {
        query: async (_p, formValues) => {
          const data = await getBankBalanceList(formValues);
          excludedFxCount.value = Number(data?.excludedFxCount || 0);
          unmatchedReceiptCount.value = Number(data?.unmatchedReceiptCount || 0);
          return { list: data?.list || [], total: data?.list?.length || 0 };
        },
      },
    },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="银行余额表">
      <template #toolbar-tools>
        <span class="text-sm text-gray-500">
          币种排除 {{ excludedFxCount }} 笔，未匹配到款 {{ unmatchedReceiptCount }} 笔
        </span>
      </template>
    </Grid>
  </Page>
</template>
