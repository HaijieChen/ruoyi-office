<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceContractApplicationApi } from '#/api/finance/contract-application';

import { nextTick, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  cancelContractApplication,
  getContractApplicationPage,
} from '#/api/finance/contract-application';
import { message, Modal } from 'ant-design-vue';

import FormModal from './modules/form.vue';
import InfoModal from './modules/info.vue';

defineOptions({ name: 'FinanceContractApplication' });

const route = useRoute();
const router = useRouter();

const [CreateModal, createModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: InfoModal,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  createModalApi.setData({});
  createModalApi.open();
}

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

/** BPM 详情 / 深链：?openResubmit=<appId> 打开驳回后重提表单 */
function resolveOpenResubmitId(): number | undefined {
  const raw = route.query.openResubmit;
  if (raw == null || raw === '') return undefined;
  const v = Array.isArray(raw) ? raw[0] : raw;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

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

function handleResubmit(row: FinanceContractApplicationApi.Application) {
  if (row.approvalStatus !== 'REJECTED') {
    message.warning('仅驳回状态可重提');
    return;
  }
  createModalApi.setData({ id: row.id, mode: 'resubmit' });
  createModalApi.open();
}

function handleDetail(row: FinanceContractApplicationApi.Application) {
  detailModalApi.setData({ id: row.id });
  detailModalApi.open();
}

function handleCancel(row: FinanceContractApplicationApi.Application) {
  if (row.approvalStatus !== 'PENDING') {
    message.warning('仅审批中可撤回');
    return;
  }
  Modal.confirm({
    title: '确认撤回？',
    content: '进入用印节点后不可撤回。撤回后须新建申请。',
    onOk: async () => {
      await cancelContractApplication(row.id);
      message.success('已撤回');
      handleRefresh();
    },
  });
}

function displayStatus(row: FinanceContractApplicationApi.Application) {
  if (row.voided) return '已作废';
  const a = row.approvalStatus;
  if (a === 'PENDING') {
    return row.currentNodeName
      ? `审批中 · ${row.currentNodeName}`
      : '审批中';
  }
  if (a === 'REJECTED') return '已驳回';
  if (a === 'CANCELLED') return '已取消';
  if (a === 'APPROVED') return '已通过';
  return a || '-';
}

const FILE_TYPE_OPTIONS = [
  { label: '采购合同', value: '采购合同' },
  { label: '销售合同', value: '销售合同' },
  { label: '租赁合同', value: '租赁合同' },
  { label: '借款合同', value: '借款合同' },
  { label: '推广充值业务合同', value: '推广充值业务合同' },
];

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'applicationNo',
        label: '业务单号',
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
        fieldName: 'counterpartyName',
        label: '对方名称',
        component: 'Input',
      },
      {
        fieldName: 'fileType',
        label: '文件类型',
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: FILE_TYPE_OPTIONS,
        },
      },
      {
        fieldName: 'signCompany',
        label: '签约主体',
        component: 'Input',
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'applicationNo', title: '业务单号', minWidth: 150 },
      { field: 'counterpartyName', title: '对方', minWidth: 120 },
      { field: 'signCompany', title: '签约主体', minWidth: 100 },
      { field: 'fileType', title: '文件类型', minWidth: 110 },
      { field: 'fileName', title: '文件名称', minWidth: 140 },
      {
        field: 'contractAmount',
        title: '合同金额',
        minWidth: 100,
        formatter: ({ row }) =>
          row.amountNa ? '不适用' : (row.contractAmount ?? '-'),
      },
      {
        field: 'statusDisplay',
        title: '状态',
        minWidth: 160,
        formatter: ({ row }) => displayStatus(row),
      },
      { field: 'endDate', title: '结束日', minWidth: 110 },
      {
        field: 'createTime',
        title: '创建时间',
        minWidth: 160,
        formatter: 'formatDateTime',
      },
      {
        field: 'actions',
        title: '操作',
        width: 220,
        fixed: 'right',
        slots: { default: 'actions' },
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return getContractApplicationPage({
            ...(formValues as FinanceContractApplicationApi.PageQuery),
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceContractApplicationApi.Application>,
});
</script>

<template>
  <Page auto-content-height>
    <CreateModal @success="handleRefresh" />
    <DetailModal />
    <Grid table-title="合同签约申请">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '提交合同签约',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:contract-application:create'],
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
              auth: ['finance:contract-application:query'],
              onClick: () => handleDetail(row),
            },
            {
              label: '重提',
              auth: ['finance:contract-application:resubmit'],
              ifShow: row.approvalStatus === 'REJECTED' && !row.voided,
              onClick: () => handleResubmit(row),
            },
            {
              label: '撤回',
              auth: ['finance:contract-application:create'],
              ifShow: row.approvalStatus === 'PENDING' && !row.voided,
              onClick: () => handleCancel(row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
