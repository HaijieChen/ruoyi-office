<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceCustomerCompanyApi } from '#/api/finance/customer-company';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getCustomerCompanyPage,
  updateCustomerCompanyStatus,
} from '#/api/finance/customer-company';
import { message } from 'ant-design-vue';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';

defineOptions({ name: 'FinanceCustomerCompany' });

const [CustomerCompanyFormModal, formModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  formModalApi.setData({});
  formModalApi.open();
}

function handleEdit(row: FinanceCustomerCompanyApi.CustomerCompany) {
  formModalApi.setData({ id: row.id });
  formModalApi.open();
}

async function handleToggleStatus(row: FinanceCustomerCompanyApi.CustomerCompany) {
  const next = row.status === 1 ? 0 : 1;
  await updateCustomerCompanyStatus(row.id, next);
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
          const params = formValues as FinanceCustomerCompanyApi.PageQuery;
          return getCustomerCompanyPage({
            ...params,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceCustomerCompanyApi.CustomerCompany>,
});
</script>

<template>
  <Page auto-content-height>
    <CustomerCompanyFormModal @success="handleRefresh" />
    <Grid table-title="客户公司（购方档案）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:customer-company:create'],
              onClick: handleCreate,
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
              auth: ['finance:customer-company:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: row.status === 1 ? '启用' : '停用',
              type: 'link',
              auth: ['finance:customer-company:update'],
              onClick: handleToggleStatus.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
