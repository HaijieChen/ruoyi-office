<script lang="ts" setup>
/**
 * 开票申请 · BPM 自定义表单「查看」组件。
 * 由 processInstance/detail 经 formCustomViewPath 动态加载，
 * props.id = processInstance.businessKey（开票申请主键）。
 * 只读展示；通过/驳回走详情页操作条，不在此页办票。
 */
import type { FinanceInvoiceApplicationApi } from '#/api/finance/invoice-application';

import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Descriptions, DescriptionsItem, Spin, Table, Tag, message } from 'ant-design-vue';

import { getInvoiceApplication } from '#/api/finance/invoice-application';
import { displayDateTime } from '#/utils/display-time';
import PrintVoucher from '../modules/print-voucher.vue';

defineOptions({ name: 'FinanceInvoiceApplicationInfo' });

const props = defineProps<{
  activityNodes?: any[];
  id?: number | string;
  isApproval?: boolean;
  nodeKey?: string;
  nodeKeyName?: string;
  processDefinition?: any;
  processInstance?: any;
}>();

const route = useRoute();
const loading = ref(false);
const detail = ref<FinanceInvoiceApplicationApi.Application | null>(null);

function resolveId(): number | undefined {
  if (props.id != null && props.id !== '') {
    const n = typeof props.id === 'string' ? Number(props.id) : props.id;
    return Number.isFinite(n) ? n : undefined;
  }
  const q = route.query.id;
  if (q != null && q !== '') {
    const n = Number(q);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
}

function displayTime(val?: null | number | string) {
  return displayDateTime(val);
}

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

const issueFileColumns = [
  {
    title: '附件',
    dataIndex: 'fileName',
    key: 'fileName',
    ellipsis: true,
    customRender: ({
      record,
    }: {
      record: FinanceInvoiceApplicationApi.IssueFile;
    }) => record.fileName || record.fileUrl || '-',
  },
  {
    title: '金额',
    dataIndex: 'amount',
    key: 'amount',
    width: 110,
    customRender: ({ text }: { text?: number }) =>
      text != null ? `¥${Number(text).toFixed(2)}` : '-',
  },
  {
    title: '发票号',
    dataIndex: 'invoiceNo',
    key: 'invoiceNo',
    width: 140,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    title: '开票日期',
    dataIndex: 'invoiceDate',
    key: 'invoiceDate',
    width: 120,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    title: '上传时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 160,
    customRender: ({ text }: { text?: string }) => displayTime(text) || '-',
  },
];

const lineColumns = [
  {
    title: '商务单号',
    dataIndex: 'businessOrderNo',
    key: 'businessOrderNo',
    width: 160,
    customRender: ({
      record,
    }: {
      record: FinanceInvoiceApplicationApi.Line;
    }) =>
      record.businessOrderNo ||
      (record.businessOrderId != null ? `#${record.businessOrderId}` : '-'),
  },
  {
    title: '合同业务单号（历史）',
    dataIndex: 'contractApplicationNo',
    key: 'contractApplicationNo',
    width: 160,
    customRender: ({
      record,
    }: {
      record: FinanceInvoiceApplicationApi.Line;
    }) => {
      if (record.historySourceContractUnproven || !record.sourceContractApplicationId) {
        return '历史未证实';
      }
      return record.contractApplicationNo || `#${record.sourceContractApplicationId}`;
    },
  },
  {
    title: '产品类型（历史）',
    dataIndex: 'productType',
    key: 'productType',
    width: 130,
    customRender: ({
      record,
    }: {
      record: FinanceInvoiceApplicationApi.Line;
    }) => {
      if (record.historyProductUnproven || !record.productType) {
        return '历史未证实';
      }
      return record.productType;
    },
  },
  {
    title: '当前产品',
    dataIndex: 'currentProductType',
    key: 'currentProductType',
    width: 100,
    customRender: ({ text }: { text?: string }) => text || '-',
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
    title: '备注',
    dataIndex: 'remark',
    key: 'remark',
    ellipsis: true,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    title: '主体公司',
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
  {
    title: '开票时间',
    dataIndex: 'issuedAt',
    key: 'issuedAt',
    width: 160,
    customRender: ({ text }: { text?: unknown }) => displayTime(text as any),
  },
];

async function loadData() {
  const id = resolveId();
  if (id == null) {
    detail.value = null;
    return;
  }
  loading.value = true;
  try {
    detail.value = await getInvoiceApplication(id);
  } catch (error) {
    detail.value = null;
    const msg =
      error instanceof Error ? error.message : '加载开票申请详情失败';
    message.error(msg);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadData();
});

watch(
  () => props.id,
  () => {
    void loadData();
  },
);
</script>

<template>
  <div class="invoice-application-info p-4">
    <Spin :spinning="loading">
      <template v-if="detail">
        <div class="mb-4 flex items-center gap-2">
          <span class="text-base font-medium">开票申请</span>
          <PrintVoucher :detail="detail" />
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
          <DescriptionsItem label="主体公司">
            {{ detail.invoiceCompany || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="发票类型">
            {{ detail.invoiceType || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="产品类型">
            {{ detail.taxContent || '-' }}
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
        />

        <div v-if="detail.files?.length" class="mb-2 mt-4 text-sm font-medium">
          办票记录
        </div>
        <Table
          v-if="detail.files?.length"
          size="small"
          :columns="issueFileColumns"
          :data-source="detail.files"
          :pagination="false"
          row-key="id"
          bordered
        />
      </template>
      <div v-else-if="!loading" class="text-gray-500">
        未找到开票申请（id={{ resolveId() ?? '空' }}）。请确认流程
        businessKey 与 formCustomViewPath 配置。
      </div>
    </Spin>
  </div>
</template>
