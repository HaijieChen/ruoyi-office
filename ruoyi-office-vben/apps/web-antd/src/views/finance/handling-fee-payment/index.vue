<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceHandlingFeePaymentApi } from '#/api/finance/handling-fee-payment';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteHandlingFeePayment,
  getHandlingFeePaymentPage,
} from '#/api/finance/handling-fee-payment';
import { message, Modal } from 'ant-design-vue';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';
import ImportModal from './modules/import-modal.vue';

defineOptions({ name: 'FinanceHandlingFeePayment' });

const [PaymentFormModal, formModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

const [PaymentImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportModal,
  destroyOnClose: true,
});

function handleCreate() {
  formModalApi.setData({});
  formModalApi.open();
}

function handleImport() {
  importModalApi.open();
}

function handleEdit(row: FinanceHandlingFeePaymentApi.Record) {
  formModalApi.setData({ id: row.id });
  formModalApi.open();
}

function handleDelete(row: FinanceHandlingFeePaymentApi.Record) {
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除该手续费付款记录吗？',
    onOk: async () => {
      await deleteHandlingFeePayment(row.id);
      message.success('删除成功');
      handleRefresh();
    },
  });
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
          const params = formValues as FinanceHandlingFeePaymentApi.PageQuery;
          return getHandlingFeePaymentPage({
            ...params,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceHandlingFeePaymentApi.Record>,
});
</script>

<template>
  <Page auto-content-height>
    <PaymentFormModal @success="handleRefresh" />
    <PaymentImportModal @success="handleRefresh" />
    <Grid table-title="手续费付款">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:handling-fee-payment:create'],
              onClick: handleCreate,
            },
            {
              label: '导入',
              auth: ['finance:handling-fee-payment:import'],
              onClick: handleImport,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['finance:handling-fee-payment:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '删除',
              type: 'link',
              icon: ACTION_ICON.DELETE,
              danger: true,
              auth: ['finance:handling-fee-payment:delete'],
              onClick: handleDelete.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
