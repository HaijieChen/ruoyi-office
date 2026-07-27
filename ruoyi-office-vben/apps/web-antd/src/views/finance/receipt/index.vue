<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceBankReceiptApi } from '#/api/finance/receipt';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Modal, Table, message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteReceipt,
  getLifecycleAuditList,
  getReceiptPage,
} from '#/api/finance/receipt';

import { useGridColumns, useGridFormSchema } from './data';
import FormModal from './modules/form.vue';
import ImportForm from './modules/import.vue';
import LifecycleModal from './modules/lifecycle-modal.vue';

defineOptions({ name: 'FinanceBankReceiptPage' });

const [ImportModal, importModalApi] = useVbenModal({
  connectedComponent: ImportForm,
  destroyOnClose: true,
});

const [ReceiptFormModal, formModalApi] = useVbenModal({
  connectedComponent: FormModal,
  destroyOnClose: true,
});

const lifecycleModalRef = ref<InstanceType<typeof LifecycleModal>>();

const auditVisible = ref(false);
const auditLogs = ref<FinanceBankReceiptApi.LifecycleAuditVO[]>([]);
const auditColumns = [
  {
    title: '操作',
    dataIndex: 'action',
    key: 'action',
    customRender: ({ text }: { text: number }) =>
      text === 1 ? '关闭' : text === 2 ? '重开' : text,
  },
  { title: '操作人ID', dataIndex: 'operatorId', key: 'operatorId' },
  { title: '原因', dataIndex: 'reason', key: 'reason' },
  { title: '时间', dataIndex: 'actionTime', key: 'actionTime' },
];

function handleRefresh() {
  gridApi.query();
}

function handleImport() {
  importModalApi.open();
}

function handleCreate() {
  formModalApi.setData({});
  formModalApi.open();
}

function handleEdit(row: FinanceBankReceiptApi.BankReceipt) {
  if (row.claimStatus !== 0 || Number(row.claimedAmount) !== 0) {
    message.warning('仅未认领且无认领金额的到款可编辑');
    return;
  }
  formModalApi.setData({ id: row.id });
  formModalApi.open();
}

function handleDelete(row: FinanceBankReceiptApi.BankReceipt) {
  if (row.claimStatus !== 0 || Number(row.claimedAmount) !== 0) {
    message.warning('仅未认领且无认领金额的到款可删除');
    return;
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定删除到款「${row.receiptNo}」吗？`,
    onOk: async () => {
      await deleteReceipt([row.id]);
      message.success('删除成功');
      handleRefresh();
    },
  });
}

function handleClose(row: FinanceBankReceiptApi.BankReceipt) {
  lifecycleModalRef.value?.open({ action: 'close', receiptId: row.id });
}

function handleReopen(row: FinanceBankReceiptApi.BankReceipt) {
  lifecycleModalRef.value?.open({ action: 'reopen', receiptId: row.id });
}

async function handleAudit(row: FinanceBankReceiptApi.BankReceipt) {
  auditLogs.value = await getLifecycleAuditList(row.id);
  auditVisible.value = true;
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
          const params = formValues as FinanceBankReceiptApi.ReceiptPageQuery;
          return getReceiptPage({
            ...params,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<FinanceBankReceiptApi.BankReceipt>,
});
</script>

<template>
  <Page auto-content-height>
    <ImportModal @success="handleRefresh" />
    <ReceiptFormModal @success="handleRefresh" />
    <LifecycleModal ref="lifecycleModalRef" @success="handleRefresh" />

    <Modal
      v-model:open="auditVisible"
      title="生命周期审计"
      :footer="null"
      width="720px"
    >
      <Table
        :data-source="auditLogs"
        :columns="auditColumns"
        :pagination="false"
        size="small"
        row-key="id"
      />
    </Modal>

    <Grid table-title="银行到款">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:receipt:create'],
              onClick: handleCreate,
            },
            {
              label: '导入回单',
              type: 'default',
              icon: ACTION_ICON.UPLOAD,
              auth: ['finance:receipt:import'],
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
              auth: ['finance:receipt:update'],
              ifShow: row.claimStatus === 0 && Number(row.claimedAmount) === 0,
              onClick: () => handleEdit(row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              auth: ['finance:receipt:delete'],
              ifShow: row.claimStatus === 0 && Number(row.claimedAmount) === 0,
              onClick: () => handleDelete(row),
            },
            {
              label: '关闭',
              type: 'link',
              auth: ['finance:receipt:close'],
              ifShow:
                (row.claimStatus === 0 || row.claimStatus === 1) &&
                Number(row.unclaimedAmount) > 0,
              onClick: () => handleClose(row),
            },
            {
              label: '重开',
              type: 'link',
              auth: ['finance:receipt:reopen'],
              ifShow: row.claimStatus === 3,
              onClick: () => handleReopen(row),
            },
            {
              label: '审计',
              type: 'link',
              auth: ['finance:receipt:audit-query'],
              onClick: () => handleAudit(row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
