<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  exportArDetailExcel,
  getArDetailPage,
} from '#/api/finance/report/ar-detail';

import { useGridColumns, useGridFormSchema } from './data';

defineOptions({ name: 'FinanceArDetailReport' });

const excludedNonCnyCount = ref(0);

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const data = await getArDetailPage({
            ...formValues,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
          excludedNonCnyCount.value = Number(data?.excludedNonCnyCount || 0);
          return data;
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions,
});

async function handleExport() {
  const formValues = (await gridApi.formApi.getValues()) || {};
  await exportArDetailExcel(formValues);
}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="应收明细">
      <template #toolbar-tools>
        <span class="mr-3 text-sm text-gray-500">
          已排除非人民币 {{ excludedNonCnyCount }} 笔
        </span>
        <TableAction
          :actions="[
            {
              label: '导出 Excel',
              type: 'primary',
              auth: ['finance:report-ar:query'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
