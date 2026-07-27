<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceBusinessOrderApi } from '#/api/finance/business-order';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteBusinessOrder,
  getBusinessOrderPage,
} from '#/api/finance/business-order';
import { message, Modal } from 'ant-design-vue';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';
import ImportModal from './modules/import-modal.vue';

defineOptions({ name: 'FinanceBusinessOrder' });

const [BusinessOrderFormModal, formModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

const [BusinessOrderImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportModal,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  formModalApi.setData({});
  formModalApi.open();
}

function handleEdit(row: FinanceBusinessOrderApi.BusinessOrder) {
  formModalApi.setData({ id: row.id });
  formModalApi.open();
}

function handleImport() {
  importModalApi.open();
}

function handleDelete(row: FinanceBusinessOrderApi.BusinessOrder) {
  if (Number(row.confirmedClaimedAmount) > 0) {
    message.warning('已有确认认领金额的商务单不能删除');
    return;
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除签单「${row.orderNo}」吗？`,
    onOk: async () => {
      await deleteBusinessOrder([row.id]);
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
          const params = formValues as FinanceBusinessOrderApi.PageQuery;
          return getBusinessOrderPage({
            ...params,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceBusinessOrderApi.BusinessOrder>,
});
</script>

<template>
  <Page auto-content-height>
    <BusinessOrderFormModal @success="handleRefresh" />
    <BusinessOrderImportModal @success="handleRefresh" />
    <Grid table-title="签单管理">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:business-order:create'],
              onClick: handleCreate,
            },
            {
              label: '导入',
              type: 'default',
              icon: ACTION_ICON.UPLOAD,
              auth: ['finance:business-order:import'],
              onClick: handleImport,
            },
          ]"
        />
      </template>
      <template #action="{ row }">
        <TableAction
          :actions="[
            {
              label: '编辑',
              type: 'link',
              auth: ['finance:business-order:update'],
              onClick: () => handleEdit(row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              auth: ['finance:business-order:delete'],
              ifShow: Number(row.confirmedClaimedAmount) === 0,
              onClick: () => handleDelete(row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
