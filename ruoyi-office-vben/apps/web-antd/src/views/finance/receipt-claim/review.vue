<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceReceiptClaimApi } from '#/api/finance/receipt-claim';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Form, FormItem, Input, Modal, Table } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  confirmClaim,
  getRevokeAuditList,
  getReviewClaim,
  getReviewPage,
  rejectClaim,
  revokeClaim,
} from '#/api/finance/receipt-claim';

defineOptions({ name: 'FinanceReceiptClaimReviewPage' });

const STATUS_LABEL: Record<number, string> = {
  0: '待确认',
  1: '已通过',
  2: '已驳回',
  3: '已撤销',
};

/* ---------- 驳回弹窗 ---------- */
const rejectVisible = ref(false);
const rejectForm = ref<{ id: number; reason: string }>({ id: 0, reason: '' });

function openRejectModal(row: FinanceReceiptClaimApi.ReceiptClaim) {
  rejectForm.value = { id: row.id, reason: '' };
  rejectVisible.value = true;
}

async function handleRejectOk() {
  await rejectClaim(rejectForm.value);
  rejectVisible.value = false;
  gridApi.query();
}

/* ---------- 确认 ---------- */
async function handleConfirm(row: FinanceReceiptClaimApi.ReceiptClaim) {
  await confirmClaim(row.id);
  gridApi.query();
}

/* ---------- 撤销弹窗 ---------- */
const revokeVisible = ref(false);
const revokeForm = ref<{ id: number; reason: string }>({ id: 0, reason: '' });

function openRevokeModal(row: FinanceReceiptClaimApi.ReceiptClaim) {
  revokeForm.value = { id: row.id, reason: '' };
  revokeVisible.value = true;
}

async function handleRevokeOk() {
  await revokeClaim(revokeForm.value);
  revokeVisible.value = false;
  gridApi.query();
}

/* ---------- 审计历史弹窗 ---------- */
const auditVisible = ref(false);
const auditLogs = ref<FinanceReceiptClaimApi.AuditLog[]>([]);

const auditColumns = [
  {
    title: '操作',
    dataIndex: 'action',
    key: 'action',
    customRender: ({ text }: { text?: string }) => text || '撤销',
  },
  {
    title: '操作人',
    dataIndex: 'operatorName',
    key: 'operatorName',
    customRender: ({
      text,
      record,
    }: {
      text?: string;
      record: FinanceReceiptClaimApi.AuditLog;
    }) =>
      text ||
      record.reviewerName ||
      (record.operatorId != null
        ? `ID:${record.operatorId}`
        : record.reviewerId != null
          ? `ID:${record.reviewerId}`
          : '—'),
  },
  {
    title: '原因',
    dataIndex: 'reason',
    key: 'reason',
    customRender: ({
      text,
      record,
    }: {
      text?: string;
      record: FinanceReceiptClaimApi.AuditLog;
    }) => text || record.revokeReason || '—',
  },
  {
    title: '时间',
    dataIndex: 'createTime',
    key: 'createTime',
    customRender: ({
      text,
      record,
    }: {
      text?: string;
      record: FinanceReceiptClaimApi.AuditLog;
    }) => formatDateTime((text || record.revokeTime) as any) || '—',
  },
];

async function openAuditModal(row: FinanceReceiptClaimApi.ReceiptClaim) {
  auditLogs.value = await getRevokeAuditList(row.id);
  auditVisible.value = true;
}

/* ---------- 详情抽屉（review-get，含购方） ---------- */
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<FinanceReceiptClaimApi.ReceiptClaim | null>(null);

async function openDetailModal(row: FinanceReceiptClaimApi.ReceiptClaim) {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    detail.value = await getReviewClaim(row.id);
  } finally {
    detailLoading.value = false;
  }
}

/* ---------- 表格 ---------- */
const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Select',
        fieldName: 'status',
        label: '状态',
        componentProps: {
          allowClear: true,
          options: [
            { label: '待确认', value: 0 },
            { label: '已通过', value: 1 },
            { label: '已驳回', value: 2 },
            { label: '已撤销', value: 3 },
          ],
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { title: '认领单号', field: 'id', minWidth: 100 },
      { title: '认领人', field: 'claimantId', minWidth: 100 },
      {
        title: '认领总金额',
        field: 'totalClaimAmount',
        minWidth: 130,
        formatter: ({ cellValue }) =>
          cellValue != null ? `¥${Number(cellValue).toFixed(2)}` : '-',
      },
      {
        title: '状态',
        field: 'status',
        minWidth: 100,
        formatter: ({ cellValue }) => STATUS_LABEL[cellValue as number] ?? '-',
      },
      { title: '说明', field: 'remark', minWidth: 160 },
      { title: '创建时间', field: 'createTime', minWidth: 160 },
      {
        title: '操作',
        width: 160,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    expandConfig: {
      lazy: false,
      visibleMethod: () => true,
    },
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const params = formValues as FinanceReceiptClaimApi.ClaimPageQuery;
          return getReviewPage({
            ...params,
            pageNo: page.currentPage,
            pageSize: page.pageSize,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<FinanceReceiptClaimApi.ReceiptClaim>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="认领单复核">
      <!-- 明细子表 -->
      <template #expand="{ row }">
        <table style="width: 100%; border-collapse: collapse; font-size: 12px">
          <thead>
            <tr style="background: #fafafa">
              <th style="padding: 4px 8px; text-align: left">回单流水号</th>
              <th style="padding: 4px 8px; text-align: left">付款方</th>
              <th style="padding: 4px 8px; text-align: left">银行流水号</th>
              <th style="padding: 4px 8px; text-align: left">开票申请</th>
              <th style="padding: 4px 8px; text-align: left">购方</th>
              <th style="padding: 4px 8px; text-align: right">认领金额</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in (row as FinanceReceiptClaimApi.ReceiptClaim).items"
              :key="item.id"
            >
              <td style="padding: 4px 8px">{{ item.receiptNo }}</td>
              <td style="padding: 4px 8px">{{ item.payerName }}</td>
              <td style="padding: 4px 8px">{{ item.bankSerialNo }}</td>
              <td style="padding: 4px 8px">{{ item.invoiceApplicationNo || item.businessOrderNo }}</td>
              <td style="padding: 4px 8px">{{ item.buyerName || item.businessSubject || '—' }}</td>
              <td style="padding: 4px 8px; text-align: right">
                ¥{{ Number(item.claimAmount).toFixed(2) }}
              </td>
            </tr>
          </tbody>
        </table>
      </template>

      <!-- 行操作 -->
      <template #action="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              auth: ['finance:receipt-claim:review'],
              onClick: () => openDetailModal(row),
            },
            {
              label: '确认',
              type: 'link',
               icon: ACTION_ICON.AUDIT,
              auth: ['finance:receipt-claim:confirm'],
              ifShow: (row as FinanceReceiptClaimApi.ReceiptClaim).status === 0,
              onClick: () => handleConfirm(row),
            },
            {
              label: '驳回',
              type: 'link',
              icon: ACTION_ICON.DELETE,
              auth: ['finance:receipt-claim:reject'],
              ifShow: (row as FinanceReceiptClaimApi.ReceiptClaim).status === 0,
              onClick: () => openRejectModal(row),
            },
            {
              label: '撤销',
              type: 'link',
              danger: true,
               icon: ACTION_ICON.CLOSE,
              auth: ['finance:receipt-claim:review'],
              ifShow: (row as FinanceReceiptClaimApi.ReceiptClaim).status === 1,
              onClick: () => openRevokeModal(row),
            },
            {
              label: '撤销历史',
              type: 'link',
              auth: ['finance:receipt-claim:revoke'],
              ifShow: (row as FinanceReceiptClaimApi.ReceiptClaim).status === 3,
              onClick: () => openAuditModal(row),
            },
          ]"
        />
      </template>
    </Grid>

    <!-- 驳回弹窗 -->
    <Modal
      v-model:open="rejectVisible"
      title="驳回认领单"
      @ok="handleRejectOk"
    >
      <Form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <FormItem label="驳回原因" required>
          <Input v-model:value="rejectForm.reason" placeholder="请输入驳回原因" />
        </FormItem>
      </Form>
    </Modal>

    <!-- 撤销弹窗 -->
    <Modal
      v-model:open="revokeVisible"
      title="撤销认领单"
      @ok="handleRevokeOk"
    >
      <Form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <FormItem label="撤销原因" required>
          <Input v-model:value="revokeForm.reason" placeholder="请输入撤销原因" />
        </FormItem>
      </Form>
    </Modal>

    <!-- 审计历史弹窗 -->
    <Modal
      v-model:open="auditVisible"
      title="撤销历史"
      :footer="null"
      width="700"
    >
      <Table
        :columns="auditColumns"
        :data-source="auditLogs"
        :pagination="false"
        row-key="id"
        size="small"
      />
    </Modal>

    <!-- 复核详情（review-get） -->
    <Modal
      v-model:open="detailVisible"
      title="认领单详情"
      :footer="null"
      width="800"
      :confirm-loading="detailLoading"
    >
      <div v-if="detailLoading" class="py-8 text-center text-gray-400">
        加载中…
      </div>
      <template v-else-if="detail">
        <div class="mb-3 text-sm text-gray-600">
          单号 {{ detail.id }} · 状态
          {{ STATUS_LABEL[detail.status] ?? detail.status }} · 总额 ¥{{
            Number(detail.totalClaimAmount).toFixed(2)
          }}
        </div>
        <Table
          size="small"
          :pagination="false"
          row-key="id"
          :data-source="detail.items || []"
          :columns="[
            { title: '到款流水', dataIndex: 'receiptNo', key: 'receiptNo' },
            { title: '付款方', dataIndex: 'payerName', key: 'payerName' },
            {
              title: '开票申请',
              dataIndex: 'invoiceApplicationNo',
              key: 'invoiceApplicationNo',
            },
            { title: '购方名称', dataIndex: 'buyerName', key: 'buyerName' },
            {
              title: '认领金额',
              dataIndex: 'claimAmount',
              key: 'claimAmount',
              customRender: ({ text }: { text?: number }) =>
                text != null ? `¥${Number(text).toFixed(2)}` : '—',
            },
          ]"
        />
      </template>
    </Modal>
  </Page>
</template>
