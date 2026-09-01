<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceExpenseApi } from '#/api/finance/expense-reimbursement';

import { Page } from '@vben/common-ui';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getExpenseReimbursementPage } from '#/api/finance/expense-reimbursement';
import { router } from '#/router';

defineOptions({ name: 'FinanceExpenseReimbursement' });

function handleCreate() {
  router.push({ path: '/finance/expense-reimbursement/create' });
}

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { fieldName: 'periodLabel', label: '期间', component: 'Input' },
      { fieldName: 'status', label: '状态', component: 'Input' },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'processTitle', title: '标题', minWidth: 200 },
      { field: 'applicationNo', title: '单据编号', width: 160 },
      { field: 'periodLabel', title: '期间', width: 100 },
      { field: 'applyAmount', title: '申请金额', width: 110 },
      { field: 'approvedAmount', title: '实报', width: 110 },
      { field: 'status', title: '状态', width: 100 },
      { field: 'applyDate', title: '申请日', width: 120 },
      { slots: { default: 'action' }, title: '操作', width: 100 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return getExpenseReimbursementPage({
            ...formValues,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceExpenseApi.Bill>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="费用报销">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            { label: '发起报销', type: 'primary', onClick: handleCreate },
          ]"
        />
      </template>
      <template #action="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              onClick: () =>
                router.push({
                  path: '/finance/expense-reimbursement/detail',
                  query: { id: row.id },
                }),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
