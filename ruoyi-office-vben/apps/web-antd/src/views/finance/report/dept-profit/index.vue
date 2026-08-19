<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getDeptProfitList } from '#/api/finance/report/dept-profit';

defineOptions({ name: 'FinanceDeptProfitReport' });

const currentMonth = new Date().toISOString().slice(0, 7);
const header = ref({
  excludedNonCnyCount: 0,
  salaryTaxPaidAmount: 0,
  salaryAllocationAmount: 0,
  unallocatedResidualAmount: 0,
});

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { fieldName: 'fromMonth', label: '开始月份', component: 'Input', defaultValue: currentMonth },
      { fieldName: 'toMonth', label: '结束月份', component: 'Input', defaultValue: currentMonth },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'deptName', title: '部门', minWidth: 140 },
      { field: 'incomeAmount', title: '收入', minWidth: 110 },
      { field: 'costAmount', title: '业务成本', minWidth: 110 },
      { field: 'reimbursementAmount', title: '报销', minWidth: 100 },
      { field: 'allocationAmount', title: '分摊', minWidth: 100 },
      { field: 'profitAmount', title: '利润', minWidth: 110 },
    ],
    height: 'auto',
    pagerConfig: { enabled: false },
    proxyConfig: {
      ajax: {
        query: async (_p, formValues) => {
          const data = await getDeptProfitList(formValues);
          header.value = {
            excludedNonCnyCount: Number(data?.excludedNonCnyCount || 0),
            salaryTaxPaidAmount: Number(data?.salaryTaxPaidAmount || 0),
            salaryAllocationAmount: Number(data?.salaryAllocationAmount || 0),
            unallocatedResidualAmount: Number(data?.unallocatedResidualAmount || 0),
          };
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
    <Grid table-title="部门利润表">
      <template #toolbar-tools>
        <span class="text-sm text-gray-500">
          薪资已付 {{ header.salaryTaxPaidAmount }} / 薪资分摊
          {{ header.salaryAllocationAmount }} / 未分摊
          {{ header.unallocatedResidualAmount }} ；排除非人民币
          {{ header.excludedNonCnyCount }}
        </span>
      </template>
    </Grid>
  </Page>
</template>
