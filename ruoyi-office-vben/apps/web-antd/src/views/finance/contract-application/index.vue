<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceContractApplicationApi } from '#/api/finance/contract-application';

import { nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  cancelContractApplication,
  deleteContractApplication,
  getContractApplicationPage,
  recordContractArchive,
} from '#/api/finance/contract-application';
import { FileUpload } from '#/components/upload';
import { message, Modal } from 'ant-design-vue';

import { displayDate } from '#/utils/display-time';

import FormModal from './modules/form.vue';
import ImportModal from './modules/import-modal.vue';
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

const [ContractApplicationImportModal, importModalApi] = useVbenModal({
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

function openImport() {
  importModalApi.open();
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

const archiveFiles = ref<string[]>([]);
const archiveTargetId = ref<number | null>(null);
const archiveSubmitting = ref(false);
const archiveModalOpen = ref(false);

function handleArchive(row: FinanceContractApplicationApi.Application) {
  if (row.approvalStatus !== 'APPROVED' || row.archivedAt || row.voided) {
    message.warning('仅已通过且未归档的合同可归档');
    return;
  }
  archiveTargetId.value = row.id;
  archiveFiles.value = [];
  archiveModalOpen.value = true;
}

async function submitArchive() {
  const id = archiveTargetId.value;
  const files = (archiveFiles.value || []).map((s) => String(s || '').trim()).filter(Boolean);
  if (id == null) return;
  if (files.length === 0) {
    message.warning('请上传至少一份归档资料');
    return;
  }
  archiveSubmitting.value = true;
  try {
    await recordContractArchive(id, files);
    message.success('已归档');
    archiveModalOpen.value = false;
    handleRefresh();
  } catch (error) {
    const msg = error instanceof Error ? error.message : '归档失败';
    message.error(msg);
  } finally {
    archiveSubmitting.value = false;
  }
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

function handleDelete(row: FinanceContractApplicationApi.Application) {
  if (row.approvalStatus === 'PENDING' && !row.voided) {
    message.warning('审批中请先撤回，不能直接删除');
    return;
  }
  Modal.confirm({
    title: '确认删除该合同签约？',
    content:
      '删除后列表不再显示，同一业务单号可再导入。已被商务单或付款引用的不能删。',
    okType: 'danger',
    onOk: async () => {
      await deleteContractApplication(row.id);
      message.success('已删除');
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
  if (a === 'APPROVED') return row.archivedAt ? '已通过 · 已归档' : '已通过';
  return a || '-';
}

const FILE_TYPE_OPTIONS = [
  { label: '采购合同', value: '采购合同' },
  { label: '销售合同', value: '销售合同' },
  { label: '付款业务合同', value: '付款业务合同' },
  { label: '租赁合同', value: '租赁合同' },
  { label: '借款合同', value: '借款合同' },
  { label: '推广充值业务合同', value: '推广充值业务合同' },
  { label: '其他', value: '其他' },
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
        label: '合同类型',
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: FILE_TYPE_OPTIONS,
        },
      },
      {
        fieldName: 'signCompany',
        label: '主体公司',
        component: 'Input',
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'applicationNo', title: '业务单号', minWidth: 150 },
      { field: 'counterpartyName', title: '对方', minWidth: 120 },
      { field: 'signCompany', title: '主体公司', minWidth: 100 },
      { field: 'fileType', title: '合同类型', minWidth: 110 },
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
      {
        field: 'startDate',
        title: '起始日',
        minWidth: 110,
        formatter: ({ cellValue }) => displayDate(cellValue),
      },
      {
        field: 'endDate',
        title: '结束日',
        minWidth: 110,
        formatter: ({ cellValue }) => displayDate(cellValue),
      },
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
    <ContractApplicationImportModal @success="handleRefresh" />
    <Modal
      v-model:open="archiveModalOpen"
      title="合同归档"
      :confirm-loading="archiveSubmitting"
      ok-text="确认归档"
      @ok="submitArchive"
    >
      <p class="mb-2 text-sm text-gray-600">请上传至少一份归档资料，可多份。</p>
      <FileUpload
        v-model:value="archiveFiles"
        :max-number="10"
        :max-size="30"
        :multiple="true"
        help-text="支持 PDF/图片等，至少一份"
      />
    </Modal>
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
            {
              label: '导入',
              type: 'default',
              icon: ACTION_ICON.UPLOAD,
              auth: ['finance:contract-application:import'],
              onClick: openImport,
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
              label: '归档',
              auth: ['finance:contract-application:record-seal'],
              ifShow:
                row.approvalStatus === 'APPROVED' &&
                !row.voided &&
                !row.archivedAt,
              onClick: () => handleArchive(row),
            },
            {
              label: '撤回',
              auth: ['finance:contract-application:create'],
              ifShow: row.approvalStatus === 'PENDING' && !row.voided,
              onClick: () => handleCancel(row),
            },
            {
              label: '删除',
              danger: true,
              auth: ['finance:contract-application:import'],
              ifShow: row.approvalStatus !== 'PENDING' || !!row.voided,
              onClick: () => handleDelete(row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
