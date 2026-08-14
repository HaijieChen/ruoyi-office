<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceTaxPaymentApi } from '#/api/finance/tax-payment';

import { nextTick, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getTaxPaymentPage } from '#/api/finance/tax-payment';

import FormModal from './modules/form.vue';

defineOptions({ name: 'FinanceTaxPayment' });

const route = useRoute();
const router = useRouter();

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

/** 统一发起目录 / 深链：?openCreate=1 打开新建 */
function shouldOpenCreateFromQuery() {
  const raw = route.query.openCreate;
  if (raw == null) return false;
  const v = Array.isArray(raw) ? raw[0] : raw;
  return v === '1' || v === 'true' || v === 'create';
}

async function consumeOpenCreateQuery() {
  if (!shouldOpenCreateFromQuery()) return;
  await nextTick();
  handleCreate();
  const nextQuery = { ...route.query };
  delete nextQuery.openCreate;
  await router.replace({ path: route.path, query: nextQuery });
}

function handleResubmit(row: FinanceTaxPaymentApi.Application) {
  formModalApi.setData({ id: row.id });
  formModalApi.open();
}

function handleDetail(row: FinanceTaxPaymentApi.Application) {
  router.push({
    path: '/finance/tax-payment/detail/index',
    query: { id: String(row.id) },
  });
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
      {
        field: 'actions',
        title: '操作',
        width: 180,
        fixed: 'right',
        slots: { default: 'actions' },
      },
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

onMounted(() => {
  void consumeOpenCreateQuery();
  const open = route.query.openResubmit;
  if (open) {
    formModalApi.setData({ id: Number(open) });
    formModalApi.open();
  }
});
watch(
  () => route.query.openCreate,
  () => {
    void consumeOpenCreateQuery();
  },
);
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
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '重提',
              type: 'link',
              auth: ['finance:tax-payment:resubmit'],
              ifShow: row.status === 'REJECTED',
              onClick: handleResubmit.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
