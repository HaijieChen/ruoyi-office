<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  exportGrossMarginExcel,
  getGrossMarginPage,
} from '#/api/finance/report/gross-margin';

defineOptions({ name: 'FinanceGrossMarginReport' });

const excludedNonCnyCount = ref(0);
const currentMonth = new Date().toISOString().slice(0, 7);

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { fieldName: 'fromMonth', label: '开始月份', component: 'Input', defaultValue: currentMonth },
      { fieldName: 'toMonth', label: '结束月份', component: 'Input', defaultValue: currentMonth },
      { fieldName: 'productType', label: '产品类型', component: 'Input' },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'yearMonth', title: '月份', width: 100 },
      { field: 'deptName', title: '部门', minWidth: 120 },
      { field: 'productType', title: '产品类型', minWidth: 120 },
      { field: 'incomeAmount', title: '收入', minWidth: 110 },
      { field: 'costAmount', title: '支出', minWidth: 110 },
      { field: 'marginAmount', title: '毛利', minWidth: 110 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const data = await getGrossMarginPage({
            ...formValues,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
          excludedNonCnyCount.value = Number(data?.excludedNonCnyCount || 0);
          return data;
        },
      },
    },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions,
});

async function handleExport() {
  const formValues = (await gridApi.formApi.getValues()) || {};
  await exportGrossMarginExcel(formValues);
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="产品毛利表">
      <template #toolbar-tools>
        <span class="mr-3 text-sm text-gray-500">
          已排除非人民币 {{ excludedNonCnyCount }} 笔
        </span>
        <TableAction
          :actions="[
            {
              label: '导出 Excel',
              type: 'primary',
              auth: ['finance:report-gross-margin:query'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
