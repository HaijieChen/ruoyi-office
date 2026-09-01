<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

import { nextTick, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPaymentApplicationPage } from '#/api/finance/payment-application';
import { message } from 'ant-design-vue';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';
import DetailModal from './modules/detail.vue';
import RecordPayModal from './modules/record-pay.vue';

defineOptions({ name: 'FinancePaymentApplication' });

const route = useRoute();
const router = useRouter();

const [CreateModal, createModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});
const [DetailModalApi, detailModalApi] = useVbenModal({
  connectedComponent: DetailModal,
  destroyOnClose: true,
});
const [PayModal, payModalApi] = useVbenModal({
  connectedComponent: RecordPayModal,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  createModalApi.setData({});
  createModalApi.open();
}

function handleDetail(row: FinancePaymentApplicationApi.Application) {
  detailModalApi.setData({ id: row.id });
  detailModalApi.open();
}

function handleResubmit(row: FinancePaymentApplicationApi.Application) {
  if (row.status !== 'REJECTED') {
    message.warning('仅已驳回可重提');
    return;
  }
  createModalApi.setData({ id: row.id, mode: 'resubmit' });
  createModalApi.open();
}

async function handleRecordPay(row: FinancePaymentApplicationApi.Application) {
  payModalApi.setData({ id: row.id });
  payModalApi.open();
}

function shouldOpenCreateFromQuery() {
  const raw = route.query.openCreate;
  if (raw == null) return false;
  const v = Array.isArray(raw) ? raw[0] : raw;
  return v === '1' || v === 'true' || v === 'create';
}

function resolveOpenResubmitId(): number | undefined {
  const raw = route.query.openResubmit;
  if (raw == null || raw === '') return undefined;
  const v = Array.isArray(raw) ? raw[0] : raw;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

async function consumeOpenCreateQuery() {
  if (!shouldOpenCreateFromQuery()) return;
  await nextTick();
  handleCreate();
  const nextQuery = { ...route.query };
  delete nextQuery.openCreate;
  await router.replace({ path: route.path, query: nextQuery });
}

/** PAY-R6：BPM 详情「修改并重提」 deep link */
async function consumeOpenResubmitQuery() {
  const id = resolveOpenResubmitId();
  if (id === undefined) return;
  await nextTick();
  createModalApi.setData({ id, mode: 'resubmit' });
  createModalApi.open();
  const nextQuery = { ...route.query };
  delete nextQuery.openResubmit;
  await router.replace({ path: route.path, query: nextQuery });
}

onMounted(() => {
  void consumeOpenCreateQuery();
  void consumeOpenResubmitQuery();
});
watch(
  () => route.query.openCreate,
  () => {
    void consumeOpenCreateQuery();
  },
);
watch(
  () => route.query.openResubmit,
  () => {
    void consumeOpenResubmitQuery();
  },
);

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
          return await getPaymentApplicationPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinancePaymentApplicationApi.Application>,
});
</script>

<template>
  <Page auto-content-height>
    <CreateModal @success="handleRefresh" />
    <DetailModalApi />
    <PayModal @success="handleRefresh" />
    <Grid table-title="付款申请">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '发起付款',
              type: 'primary',
              icon: ACTION_ICON.ADD,
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
              ifShow: row.status === 'REJECTED',
              onClick: handleResubmit.bind(null, row),
            },
            {
              label: '支付',
              type: 'link',
              ifShow: row.status === 'WAIT_PAY' || row.status === 'PARTIAL_PAID',
              onClick: handleRecordPay.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
