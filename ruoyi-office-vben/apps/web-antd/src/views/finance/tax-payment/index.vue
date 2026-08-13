<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceTaxPaymentApi } from '#/api/finance/tax-payment';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getTaxPaymentPage } from '#/api/finance/tax-payment';

import FormModal from './modules/form.vue';

defineOptions({ name: 'FinanceTaxPayment' });

const [Form, formModalApi] = useVbenModal({
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

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { fieldName: 'applicationNo', label: '单号', component: 'Input' },
      {
        fieldName: 'status',
        label: '状态',
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: [
            { label: '审批中', value: 'PENDING' },
            { label: '待支付', value: 'WAIT_PAY' },
            { label: '已支付', value: 'PAID' },
            { label: '已驳回', value: 'REJECTED' },
            { label: '已撤回', value: 'CANCELLED' },
          ],
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'applicationNo', title: '单号', minWidth: 150 },
      { field: 'processTitle', title: '标题', minWidth: 180 },
      { field: 'periodLabel', title: '税款所属期', minWidth: 120 },
      { field: 'applyAmount', title: '合计金额', minWidth: 100 },
      { field: 'currency', title: '币种', width: 80 },
      { field: 'status', title: '状态', width: 100 },
      { field: 'createTime', title: '创建时间', minWidth: 160 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return getTaxPaymentPage({
            ...(formValues as FinanceTaxPaymentApi.PageQuery),
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions,
});
</script>

<template>
  <Page auto-content-height>
    <Form @success="handleRefresh" />
    <Grid table-title="税金付款申请">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新建',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:tax-payment:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
