<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceBankReceiptApi } from '#/api/finance/receipt';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getUnclaimedReceiptPage } from '#/api/finance/receipt';

import { useGridColumns, useGridFormSchema } from './data';
import ImportForm from './modules/import.vue';

defineOptions({ name: 'FinanceBankReceiptPage' });

const [ImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportForm,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleImport() {
  importModalApi.open();
}

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
          const params = formValues as FinanceBankReceiptApi.UnclaimedReceiptPageQuery;
          return getUnclaimedReceiptPage({
            ...params,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<FinanceBankReceiptApi.BankReceipt>,
});
</script>

<template>
  <Page auto-content-height>
    <ImportModal @success="handleRefresh" />
    <Grid table-title="未认领银行回单">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '导入回单',
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              auth: ['finance:receipt:import'],
              onClick: handleImport,
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
