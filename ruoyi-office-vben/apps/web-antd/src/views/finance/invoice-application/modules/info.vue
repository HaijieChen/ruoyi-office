<script lang="ts" setup>
/**
 * 开票申请 · 列表只读详情（业务 + 审批全貌）。
 * 与 BPM formCustomView `info/index.vue` 分离。
 */
import type { FinanceInvoiceApplicationApi } from '#/api/finance/invoice-application';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import {
  Descriptions,
  DescriptionsItem,
  Divider,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { getInvoiceApplication } from '#/api/finance/invoice-application';
import ApprovalOverviewPanel from '#/views/bpm/processInstance/detail/modules/approval-overview-panel.vue';

defineOptions({ name: 'FinanceInvoiceApplicationListInfo' });

const detail = ref<FinanceInvoiceApplicationApi.Application | null>(null);
const loading = ref(false);

const approvalLabel: Record<string, string> = {
  PENDING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
};

const issueLabel: Record<number, string> = {
  0: '未开票',
  1: '部分开票',
  2: '全部开票',
};

const lineColumns = [
  {
    title: '商务单 ID',
    dataIndex: 'businessOrderId',
    key: 'businessOrderId',
    width: 110,
  },
  {
    title: '开票金额',
    dataIndex: 'amount',
    key: 'amount',
    width: 120,
    customRender: ({ text }: { text: number }) =>
      text != null ? `¥${Number(text).toFixed(2)}` : '-',
  },
  {
    title: '账期',
    dataIndex: 'billingPeriod',
    key: 'billingPeriod',
    width: 120,
  },
  {
    title: '开票公司',
    dataIndex: 'invoiceCompany',
    key: 'invoiceCompany',
    ellipsis: true,
  },
  {
    title: '发票类型',
    dataIndex: 'invoiceType',
    key: 'invoiceType',
    width: 100,
  },
  {
    title: '发票号',
    dataIndex: 'invoiceNo',
    key: 'invoiceNo',
    width: 140,
  },
];

function displayTime(val?: null | number | string) {
  if (val == null || val === '') return '-';
  return (formatDateTime(val as any) as string) || String(val);
}

const [Modal, modalApi] = useVbenModal({
  showConfirmButton: false,
  cancelText: '关闭',
  class: 'w-[960px]',
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      detail.value = null;
      return;
    }
    const data = modalApi.getData<{ id?: number }>();
    if (!data?.id) return;
    loading.value = true;
    try {
      detail.value = await getInvoiceApplication(data.id);
    } catch (error) {
      detail.value = null;
      const msg =
        error instanceof Error ? error.message : '加载开票申请详情失败';
      message.error(msg);
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Modal title="开票申请详情">
    <Spin :spinning="loading">
      <template v-if="detail">
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <Tag color="blue">{{ detail.applicationNo || '-' }}</Tag>
          <Tag>
            {{ approvalLabel[detail.approvalStatus] || detail.approvalStatus }}
          </Tag>
          <Tag v-if="detail.voided" color="red">已作废</Tag>
          <Tag v-else-if="detail.approvalStatus === 'APPROVED'">
            {{ issueLabel[detail.issueStatus ?? 0] || '' }}
          </Tag>
        </div>

        <Descriptions bordered :column="2" size="small" class="mb-4">
          <DescriptionsItem label="申请单号">
            {{ detail.applicationNo || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="价税合计">
            {{
              detail.totalAmount != null
                ? `¥${Number(detail.totalAmount).toFixed(2)}`
                : '-'
            }}
          </DescriptionsItem>
          <DescriptionsItem label="购方名称">
            {{ detail.buyerName || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="购方税号">
            {{ detail.buyerTaxNo || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="地址电话" :span="2">
            {{ detail.buyerAddressPhone || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="开户行账号" :span="2">
            {{ detail.buyerBankAccount || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="开票公司">
            {{ detail.invoiceCompany || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="发票类型">
            {{ detail.invoiceType || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="特别开票要求" :span="2">
            {{ detail.specialInvoiceRequirement || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="备注" :span="2">
            {{ detail.remark || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="创建时间">
            {{ displayTime(detail.createTime) }}
          </DescriptionsItem>
          <DescriptionsItem label="流程实例">
            {{ detail.processInstanceId || '-' }}
          </DescriptionsItem>
        </Descriptions>

        <div class="mb-2 text-sm font-medium">申请明细</div>
        <Table
          size="small"
          :columns="lineColumns"
          :data-source="detail.lines || []"
          :pagination="false"
          row-key="id"
          bordered
          class="mb-2"
        />

        <Divider orientation="left" class="!mt-6">审批全貌</Divider>
        <ApprovalOverviewPanel
          :process-instance-id="detail.processInstanceId"
        />
      </template>
    </Spin>
  </Modal>
</template>
