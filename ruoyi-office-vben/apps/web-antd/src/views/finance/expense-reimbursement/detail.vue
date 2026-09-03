<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  DatePicker,
  Descriptions,
  Input,
  InputNumber,
  Select,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import { getCompanyBankAccountPage } from '#/api/finance/company-bank-account';
import {
  approveExpenseReimbursement,
  getExpenseReimbursement,
  recordPayExpenseReimbursement,
} from '#/api/finance/expense-reimbursement';
import type { FinanceExpenseApi } from '#/api/finance/expense-reimbursement';
import { getDictLabel } from '@vben/hooks';

import { FilePreviewList, FileUpload } from '#/components/upload';
import PrintVoucher from './modules/print-voucher.vue';
import PredocOverlay from './modules/predoc-overlay.vue';

defineOptions({ name: 'FinanceExpenseReimbursementDetail' });

const props = defineProps<{
  activityNodes?: any[];
  id?: number | string;
  isApproval?: boolean;
  processInstance?: any;
}>();
const { query } = useRoute();
const embedded = computed(() => !!props.processInstance);
const loading = ref(false);
const bill = ref<FinanceExpenseApi.Bill>();
const approvedAmount = ref<number>();
const financeComment = ref('');
const payDate = ref<Dayjs>();
const payVoucherUrl = ref('');
const companyBankAccountId = ref<number>();
const accountOptions = ref<{ label: string; value: number }[]>([]);
const predocOpen = ref(false);
const overlayType = ref<string>();
const overlayBillId = ref<number>();

function openPredoc(line: FinanceExpenseApi.Line) {
  overlayType.value = line.predocType;
  overlayBillId.value = line.predocBillId;
  predocOpen.value = true;
}

function predocLinkLabel(line: FinanceExpenseApi.Line) {
  return line.predocType === 'OUTING' ? '查看出外申请' : '查看出差申请';
}

const queryId = computed(() => Number(props.id || query.id));

async function load() {
  loading.value = true;
  try {
    bill.value = await getExpenseReimbursement(queryId.value);
    approvedAmount.value = Number(bill.value?.applyAmount || 0);
    accountOptions.value = [];
    if (bill.value?.status === 'WAIT_PAY' && !embedded.value) {
      try {
        const page = await getCompanyBankAccountPage(
          {
            pageNo: 1,
            pageSize: 100,
            status: 0,
          },
          { hideErrorMessage: true },
        );
        accountOptions.value = (page?.list || []).map((a) => ({
          value: a.id,
          label: `${a.accountName} ${a.accountNoMasked || ""}`,
        }));
      } catch {
        accountOptions.value = [];
      }
    }
  } finally {
    loading.value = false;
  }
}

async function onApprove() {
  if (!bill.value?.id || approvedAmount.value == null) return;
  await approveExpenseReimbursement({
    id: bill.value.id,
    approvedAmount: approvedAmount.value,
    financeComment: financeComment.value,
    taskId: String(query.taskId || ""),
  });
  message.success('已填写实报金额');
  await load();
}

async function onRecordPay() {
  if (!bill.value?.id || !companyBankAccountId.value || !payDate.value) {
    message.warning('请填写账户和支付日');
    return;
  }
  const urls = payVoucherUrl.value
    ? payVoucherUrl.value.split(',').map((s) => s.trim()).filter(Boolean)
    : [];
  await recordPayExpenseReimbursement({
    id: bill.value.id,
    companyBankAccountId: companyBankAccountId.value,
    actualPayDate: payDate.value.format('YYYY-MM-DD'),
    payVoucherUrls: urls,
    payVoucherUrl: urls.join(','),
  });
  message.success('已支付');
  await load();
}

function onVoucher(v: string | string[]) {
  const arr = Array.isArray(v) ? v : v ? [v] : [];
  payVoucherUrl.value = arr.filter(Boolean).join(',');
}

function formatFeeDate(v: unknown) {
  if (v == null || v === '') return '';
  if (Array.isArray(v) && v.length >= 3) {
    const y = v[0];
    const m = String(v[1]).padStart(2, '0');
    const d = String(v[2]).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
  if (typeof v === 'number') {
    const ms = v < 1e12 ? v * 1000 : v;
    return dayjs(ms).format('YYYY-MM-DD');
  }
  const s = String(v);
  if (/^\d{10,13}$/.test(s)) {
    const n = Number(s);
    return dayjs(n < 1e12 ? n * 1000 : n).format('YYYY-MM-DD');
  }
  return s.length >= 10 ? s.slice(0, 10) : s;
}

function categoryLabel(line: FinanceExpenseApi.Line) {
  const cat = getDictLabel('finance_expense_category', line.category) || line.category || '';
  const sub = line.subItem
    ? getDictLabel('finance_expense_subitem', line.subItem) || line.subItem
    : '';
  return [cat, sub].filter(Boolean).join('/');
}

function money(v: unknown) {
  const n = Number(v);
  if (!Number.isFinite(n)) return '-';
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '审批中', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已取消', color: 'default' },
  WAIT_PAY: { text: '待支付', color: 'warning' },
  PAID: { text: '已支付', color: 'success' },
};

const statusMeta = computed(() => STATUS_MAP[bill.value?.status || ''] || {
  text: bill.value?.status || '-',
  color: 'default',
});

const showFullAccount = computed(() => {
  const s = bill.value?.status;
  return s === 'WAIT_PAY' || s === 'PAID';
});

const displayAccount = computed(() => {
  const no = bill.value?.payeeAccountNo || '';
  if (!no) return '-';
  if (showFullAccount.value || no.length <= 4) return no;
  return `${'*'.repeat(Math.max(4, no.length - 4))}${no.slice(-4)}`;
});

const linesTotal = computed(() =>
  (bill.value?.lines || []).reduce((s, l) => s + Number(l.amount || 0), 0),
);

const lineRows = computed(() =>
  (bill.value?.lines || []).map((line, index) => ({ ...line, _rowKey: index })),
);

onMounted(load);
</script>

<template>
  <component :is="embedded ? 'div' : Page" v-bind="embedded ? {} : { autoContentHeight: true }">
    <Spin :spinning="loading">
      <div
        v-if="bill"
        class="mx-auto p-4 print:max-w-none"
        :class="embedded ? 'max-w-5xl' : 'max-w-4xl'"
      >
        <div class="mb-4 flex items-start justify-between gap-4 print:hidden">
          <div>
            <div class="text-2xl font-semibold tracking-tight">
              {{ money(bill.applyAmount) }}
            </div>
            <div class="mt-1 text-sm text-gray-600">
              付给 {{ bill.payeeAccountName || '-' }}
              <span v-if="bill.entityCompanyName"> · {{ bill.entityCompanyName }}</span>
            </div>
            <div class="mt-0.5 text-xs text-gray-400">
              {{ bill.applicationNo || '-' }}
              <span v-if="bill.periodLabel"> · {{ bill.periodLabel }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Tag :color="statusMeta.color">{{ statusMeta.text }}</Tag>
            <PrintVoucher :bill="bill" />
          </div>
        </div>
        <Descriptions bordered :column="2" size="small" class="print:hidden">
          <Descriptions.Item label="开户行">{{ bill.payeeBankName || '—' }}</Descriptions.Item>
          <Descriptions.Item label="收款账号">{{ displayAccount }}</Descriptions.Item>
          <Descriptions.Item label="申请金额">{{ money(bill.applyAmount) }}</Descriptions.Item>
          <Descriptions.Item label="实报金额">
            {{ bill.approvedAmount == null ? '待财务填写' : money(bill.approvedAmount) }}
          </Descriptions.Item>
        </Descriptions>
        <div class="mt-4 print:hidden">
          <Table
            class="expense-line-table"
            size="small"
            :pagination="false"
            :data-source="lineRows"
            row-key="_rowKey"
            :scroll="{ x: 720 }"
          >
            <Table.Column title="日期" width="110">
              <template #default="{ record }">{{ formatFeeDate(record.feeDate) }}</template>
            </Table.Column>
            <Table.Column v-if="!bill.proxyTicket" title="费用项" min-width="140">
              <template #default="{ record }">{{ categoryLabel(record) }}</template>
            </Table.Column>
            <Table.Column title="发票" width="80" data-index="invoiceType" />
            <Table.Column title="金额" width="110" align="right">
              <template #default="{ record }">{{ money(record.amount) }}</template>
            </Table.Column>
            <Table.Column title="税额" width="90" align="right">
              <template #default="{ record }">
                {{ record.taxAmount == null ? '—' : money(record.taxAmount) }}
              </template>
            </Table.Column>
            <Table.Column title="票号" width="140" data-index="invoiceNo" />
            <Table.Column title="附件" width="88" class="expense-line-attach">
              <template #default="{ record }">
                <FilePreviewList :value="record.invoiceFileUrl" />
              </template>
            </Table.Column>
            <Table.Column title="说明" min-width="140">
              <template #default="{ record }">
                <span v-if="record.stayCityTier === 'T1'">北上广深 </span>
                <span v-else-if="record.stayCityTier === 'OTHER'">其他城市 </span>
                <span v-if="record.overLimitReason">超标：{{ record.overLimitReason }} </span>
                <span>{{ record.remark || '' }}</span>
                <Button
                  v-if="record.predocProcessInstanceId"
                  type="link"
                  class="px-1"
                  @click="openPredoc(record)"
                >
                  {{ predocLinkLabel(record) }}
                </Button>
              </template>
            </Table.Column>
          </Table>
          <div class="mt-2 flex justify-end text-sm text-gray-600">
            明细合计 {{ money(linesTotal) }}
            <span
              v-if="Math.abs(linesTotal - Number(bill.applyAmount || 0)) > 0.009"
              class="ml-2 text-amber-600"
            >
              （与申请金额不一致）
            </span>
          </div>
        </div>
        <div
          v-if="(bill.extraAttachments || []).length"
          class="mt-3 print:hidden"
        >
          <div class="mb-1 text-sm font-medium">其他附件</div>
          <FilePreviewList :value="bill.extraAttachments" />
        </div>
        <div
          v-if="bill.payVoucherUrl"
          class="mt-3 print:hidden"
        >
          <div class="mb-1 text-sm font-medium">支付附件</div>
          <FilePreviewList :value="bill.payVoucherUrl" />
        </div>
        <div v-if="bill.status === 'PENDING'" class="mt-6 space-y-2 print:hidden">
          <div class="font-medium">财务实报</div>
          <InputNumber v-model:value="approvedAmount" :min="0.01" :precision="2" class="w-40" />
          <Input v-model:value="financeComment" placeholder="审核意见" />
          <Button type="primary" @click="onApprove">提交实报金额</Button>
        </div>
        <div
          v-if="bill.status === 'WAIT_PAY' && !embedded && bill.processEnded !== false"
          class="mt-6 space-y-2 print:hidden"
        >
          <div class="font-medium">支付（附件可选）</div>
          <Select v-model:value="companyBankAccountId" class="w-72" :options="accountOptions" placeholder="公司银行账户" />
          <DatePicker v-model:value="payDate" />
          <FileUpload
            :value="payVoucherUrl ? payVoucherUrl.split(',').filter(Boolean) : []"
            :max-number="30"
            :multiple="true"
            help-text="可选，最多 30 个，不识别金额"
            @update:value="onVoucher"
          />
          <Button type="primary" @click="onRecordPay">支付</Button>
        </div>
      </div>
    </Spin>
    <PredocOverlay
      v-model:open="predocOpen"
      :predoc-type="overlayType"
      :bill-id="overlayBillId"
    />
  </component>
</template>

<style scoped>
.expense-line-table :deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
}

.expense-line-table :deep(.file-upload-root),
.expense-line-table :deep(.ant-upload),
.expense-line-table :deep(.ant-upload-list),
.expense-line-table :deep(.ant-upload-list-picture-card) {
  display: flex;
  align-items: center;
  margin: 0;
}

.expense-line-table :deep(.ant-upload-list-picture-card-container) {
  margin: 0;
  float: none;
}
</style>
