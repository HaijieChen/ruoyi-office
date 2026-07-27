<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FinanceReceiptClaimApi } from '#/api/finance/receipt-claim';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Modal } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getMyClaim,
  getMyClaimPage,
  resubmitClaim,
} from '#/api/finance/receipt-claim';

import FormModal from './modules/form.vue';

defineOptions({ name: 'FinanceReceiptClaimMyPage' });

const STATUS_LABEL: Record<number, string> = {
  0: '待确认',
  1: '已通过',
  2: '已驳回',
  3: '已撤销',
};

const [ClaimFormModal, formModalApi] = useVbenModal({
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

async function handleEdit(row: FinanceReceiptClaimApi.ReceiptClaim) {
  if (row.status !== 0 && row.status !== 2) {
    message.warning('仅待确认或已驳回的认领单可修改');
    return;
  }
  const detail = await getMyClaim(row.id);
  formModalApi.setData({
    id: detail.id,
    remark: detail.remark,
    items: (detail.items || []).map((i) => ({
      receiptId: i.receiptId,
      businessOrderId: i.businessOrderId,
      claimAmount: i.claimAmount,
    })),
  });
  formModalApi.open();
}

function handleResubmit(row: FinanceReceiptClaimApi.ReceiptClaim) {
  Modal.confirm({
    title: '重新提交',
    content: `确认将认领单 #${row.id} 重新提交财务复核？`,
    onOk: async () => {
      await resubmitClaim(row.id);
      message.success('已重新提交');
      handleRefresh();
    },
  });
}

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
      { type: 'expand', width: 50 },
      { title: '认领单号', field: 'id', minWidth: 100 },
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
      { title: '驳回原因', field: 'rejectReason', minWidth: 160 },
      { title: '说明', field: 'remark', minWidth: 160 },
      { title: '创建时间', field: 'createTime', minWidth: 160 },
      {
        title: '操作',
        field: 'action',
        fixed: 'right',
        slots: { default: 'action' },
        minWidth: 160,
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
          return getMyClaimPage({
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
    <ClaimFormModal @success="handleRefresh" />
    <Grid table-title="我的认领单">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新建认领',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['finance:receipt-claim:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #action="{ row }">
        <TableAction
          :actions="[
            {
              label: '修改',
              type: 'link',
              auth: ['finance:receipt-claim:update'],
              ifShow: row.status === 0 || row.status === 2,
              onClick: () => handleEdit(row),
            },
            {
              label: '重提',
              type: 'link',
              auth: ['finance:receipt-claim:resubmit'],
              ifShow: row.status === 2,
              onClick: () => handleResubmit(row),
            },
          ]"
        />
      </template>
      <template #expand="{ row }">
        <table style="width: 100%; border-collapse: collapse; font-size: 12px">
          <thead>
            <tr style="background: #fafafa">
              <th style="padding: 4px 8px; text-align: left">回单流水号</th>
              <th style="padding: 4px 8px; text-align: left">付款方</th>
              <th style="padding: 4px 8px; text-align: left">银行流水号</th>
              <th style="padding: 4px 8px; text-align: left">商务单号</th>
              <th style="padding: 4px 8px; text-align: left">产品/主体</th>
              <th style="padding: 4px 8px; text-align: right">认领金额</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in (row as FinanceReceiptClaimApi.ReceiptClaim).items ||
              []"
              :key="item.id || `${item.receiptId}-${item.businessOrderId}`"
            >
              <td style="padding: 4px 8px">{{ item.receiptNo }}</td>
              <td style="padding: 4px 8px">{{ item.payerName }}</td>
              <td style="padding: 4px 8px">{{ item.bankSerialNo }}</td>
              <td style="padding: 4px 8px">{{ item.businessOrderNo }}</td>
              <td style="padding: 4px 8px">
                {{ item.productName || item.businessSubject || '-' }}
              </td>
              <td style="padding: 4px 8px; text-align: right">
                ¥{{ Number(item.claimAmount).toFixed(2) }}
              </td>
            </tr>
          </tbody>
        </table>
      </template>
    </Grid>
  </Page>
</template>
