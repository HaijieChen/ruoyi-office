<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceCompanyBankAccountApi } from '#/api/finance/company-bank-account';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getCompanyBankAccountPage,
  updateCompanyBankAccountStatus,
} from '#/api/finance/company-bank-account';
import { message } from 'ant-design-vue';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';
import ImportModal from './modules/import-modal.vue';

defineOptions({ name: 'FinanceCompanyBankAccount' });

const [AccountFormModal, formModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

const [AccountImportModal, importModalApi] = useVbenModal({
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

function handleEdit(row: FinanceCompanyBankAccountApi.Account) {
  formModalApi.setData({ id: row.id });
  formModalApi.open();
}

async function handleToggleStatus(row: FinanceCompanyBankAccountApi.Account) {
  const next = row.status === 1 ? 0 : 1;
  await updateCompanyBankAccountStatus(row.id, next);
  message.success(next === 1 ? '已停用' : '已启用');
  handleRefresh();
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
          const params = formValues as FinanceCompanyBankAccountApi.PageQuery;
          return getCompanyBankAccountPage({
            ...params,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceCompanyBankAccountApi.Account>,
});
</script>

<template>
  <Page auto-content-height>
    <AccountFormModal @success="handleRefresh" />
    <AccountImportModal @success="handleRefresh" />
    <Grid table-title="账户信息管理">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:company-bank-account:create'],
              onClick: handleCreate,
            },
            {
              label: '导入',
              auth: ['finance:company-bank-account:import'],
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
              auth: ['finance:company-bank-account:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: row.status === 1 ? '启用' : '停用',
              type: 'link',
              auth: ['finance:company-bank-account:update'],
              onClick: handleToggleStatus.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
