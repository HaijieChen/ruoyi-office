<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceInvoiceApplicationApi } from '#/api/finance/invoice-application';

import { nextTick, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getInvoiceApplicationPage,
  resubmitInvoiceApplication,
} from '#/api/finance/invoice-application';
import { message } from 'ant-design-vue';

import FormModal from './modules/form.vue';
import ImportModal from './modules/import-modal.vue';
import DetailModal from './modules/info.vue';
import IssueModal from './modules/issue-form.vue';

defineOptions({ name: 'FinanceInvoiceApplication' });

const route = useRoute();
const router = useRouter();

const [CreateModal, createModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

const [IssueFormModal, issueModalApi] = useVbenModal({
  connectedComponent: IssueModal,
  destroyOnClose: true,
});

const [InfoModal, infoModalApi] = useVbenModal({
  connectedComponent: DetailModal,
  destroyOnClose: true,
});

const [InvoiceImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportModal,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  createModalApi.setData({});
  createModalApi.open();
}

function handleDetail(row: FinanceInvoiceApplicationApi.Application) {
  infoModalApi.setData({ id: row.id });
  infoModalApi.open();
}

/**
 * 发起流程 catalog：formCustomCreatePath =
 * /finance/invoice-application?openCreate=1
 * 进入列表后自动打开创建弹窗，并去掉 query 避免刷新重复弹。
 */
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

onMounted(() => {
  void consumeOpenCreateQuery();
});

watch(
  () => route.query.openCreate,
  () => {
    void consumeOpenCreateQuery();
  },
);

function handleResubmit(row: FinanceInvoiceApplicationApi.Application) {
  if (row.approvalStatus !== 'REJECTED') {
    message.warning('仅驳回状态可重提');
    return;
  }
  createModalApi.setData({ id: row.id, mode: 'resubmit' });
  createModalApi.open();
}

function handleIssue(row: FinanceInvoiceApplicationApi.Application) {
  if (row.approvalStatus !== 'APPROVED') {
    message.warning('仅审批通过可办票');
    return;
  }
  issueModalApi.setData({ applicationId: row.id });
  issueModalApi.open();
}

function displayStatus(row: FinanceInvoiceApplicationApi.Application) {
  const a = row.approvalStatus;
  const issueMap: Record<number, string> = {
    0: '未开票',
    1: '部分开票',
    2: '全部开票',
  };
  if (row.voided) return '已作废';
  if (a === 'PENDING') return '审批中';
  if (a === 'REJECTED') return '已驳回';
  if (a === 'CANCELLED') return '已取消';
  if (a === 'APPROVED') return `已通过 · ${issueMap[row.issueStatus ?? 0] || ''}`;
  return a;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'applicationNo',
        label: '申请单号',
        component: 'Input',
      },
      {
        fieldName: 'approvalStatus',
        label: '审批状态',
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: [
            { label: '审批中', value: 'PENDING' },
            { label: '已通过', value: 'APPROVED' },
            { label: '已驳回', value: 'REJECTED' },
            { label: '已取消', value: 'CANCELLED' },
          ],
        },
      },
      {
        fieldName: 'buyerName',
        label: '购方名称',
        component: 'Input',
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'applicationNo', title: '申请单号', minWidth: 140 },
      { field: 'buyerName', title: '购方', minWidth: 120 },
      { field: 'totalAmount', title: '价税合计', minWidth: 100 },
      {
        field: 'statusDisplay',
        title: '状态',
        minWidth: 140,
        formatter: ({ row }) => displayStatus(row),
      },
      { field: 'processInstanceId', title: '流程实例', minWidth: 120 },
      { field: 'createTime', title: '创建时间', minWidth: 160 },
      {
        field: 'actions',
        title: '操作',
        width: 240,
        fixed: 'right',
        slots: { default: 'actions' },
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return getInvoiceApplicationPage({
            ...(formValues as FinanceInvoiceApplicationApi.PageQuery),
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceInvoiceApplicationApi.Application>,
});
</script>

<template>
  <Page auto-content-height>
    <CreateModal @success="handleRefresh" />
    <IssueFormModal @success="handleRefresh" />
    <InfoModal />
    <InvoiceImportModal @success="handleRefresh" />
    <Grid table-title="开票申请">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '提交开票申请',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:invoice-application:create'],
              onClick: handleCreate,
            },
            {
              label: '导入历史',
              auth: ['finance:invoice-application:import'],
              onClick: () => importModalApi.open(),
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              auth: ['finance:invoice-application:query'],
              onClick: () => handleDetail(row),
            },
            {
              label: '重提',
              auth: ['finance:invoice-application:resubmit'],
              ifShow: row.approvalStatus === 'REJECTED' && !row.voided,
              onClick: () => handleResubmit(row),
            },
            {
              label: '办票',
              auth: ['finance:invoice-application:issue'],
              ifShow: row.approvalStatus === 'APPROVED' && !row.voided,
              onClick: () => handleIssue(row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
