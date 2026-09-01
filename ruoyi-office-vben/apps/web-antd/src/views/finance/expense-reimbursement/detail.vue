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

import { FileUpload } from '#/components/upload';
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
    if (bill.value?.status === 'WAIT_PAY' && props.isApproval !== false) {
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
  if (!bill.value?.id || !companyBankAccountId.value || !payDate.value || !payVoucherUrl.value) {
    message.warning('请填写账户、支付日和凭证');
    return;
  }
  await recordPayExpenseReimbursement({
    id: bill.value.id,
    companyBankAccountId: companyBankAccountId.value,
    actualPayDate: payDate.value.format('YYYY-MM-DD'),
    payVoucherUrl: payVoucherUrl.value,
    taskId: String(query.taskId || ""),
  });
  message.success('已登记支付');
  await load();
}

function onVoucher(v: string | string[]) {
  payVoucherUrl.value = Array.isArray(v) ? String(v[0] || '') : String(v || '');
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

onMounted(load);
</script>

<template>
  <component :is="embedded ? 'div' : Page" v-bind="embedded ? {} : { autoContentHeight: true }">
    <Spin :spinning="loading">
      <div v-if="bill" class="mx-auto max-w-3xl p-4 print:max-w-none">
        <div class="mb-2">
          <PrintVoucher :bill="bill" />
        </div>
        <Descriptions bordered :column="2" size="small" class="print:hidden">
          <Descriptions.Item label="标题">{{ bill.processTitle }}</Descriptions.Item>
          <Descriptions.Item label="单据编号">{{ bill.applicationNo || '-' }}</Descriptions.Item>
          <Descriptions.Item label="状态">{{ bill.status }}</Descriptions.Item>
          <Descriptions.Item label="期间">{{ bill.periodLabel }}</Descriptions.Item>
          <Descriptions.Item v-if="bill.entityCompanyName" label="主体公司">{{ bill.entityCompanyName }}</Descriptions.Item>
          <Descriptions.Item label="收款户名">{{ bill.payeeAccountName }}</Descriptions.Item>
          <Descriptions.Item label="开户行">{{ bill.payeeBankName || '—' }}</Descriptions.Item>
          <Descriptions.Item label="收款账号">{{ bill.payeeAccountNo }}</Descriptions.Item>
          <Descriptions.Item label="申请金额">{{ bill.applyAmount }}</Descriptions.Item>
          <Descriptions.Item label="实报金额">{{ bill.approvedAmount }}</Descriptions.Item>
        </Descriptions>
        <div class="mt-3 text-sm print:hidden">
          <div v-for="(line, i) in bill.lines || []" :key="i">
            {{ formatFeeDate(line.feeDate) }} ·
            <span :class="bill.proxyTicket ? 'print:hidden' : ''">{{ categoryLabel(line) }}</span>
            · 发票 {{ line.invoiceType || '-' }}
            · {{ line.amount }}{{ line.taxAmount != null ? ' · 税额 ' + line.taxAmount : '' }}{{ line.invoiceNo ? ' · 票号 ' + line.invoiceNo : '' }}
            {{ line.stayCityTier === 'T1' ? ' · 北上广深' : line.stayCityTier === 'OTHER' ? ' · 其他城市' : '' }}
            {{ line.overLimitReason ? ` · 超标：${line.overLimitReason}` : "" }}
            {{ line.remark ? ` · ${line.remark}` : "" }}
            <Button
              v-if="line.predocProcessInstanceId"
              type="link"
              class="px-1"
              @click="openPredoc(line)"
            >
              {{ predocLinkLabel(line) }}
            </Button>
          </div>
        </div>
        <div
          v-if="(bill.extraAttachments || []).length"
          class="mt-3 print:hidden"
        >
          <div class="mb-1 text-sm font-medium">其他附件</div>
          <FileUpload
            :value="bill.extraAttachments || []"
            :max-number="30"
            disabled
          />
        </div>
        <div v-if="bill.status === 'PENDING'" class="mt-6 space-y-2">
          <div class="font-medium">财务实报</div>
          <InputNumber v-model:value="approvedAmount" :min="0.01" :precision="2" class="w-40" />
          <Input v-model:value="financeComment" placeholder="审核意见" />
          <Button type="primary" @click="onApprove">提交实报金额</Button>
        </div>
        <div v-if="bill.status === 'WAIT_PAY' && isApproval !== false" class="mt-6 space-y-2">
          <div class="font-medium">出纳登记</div>
          <Select v-model:value="companyBankAccountId" class="w-72" :options="accountOptions" placeholder="公司银行账户" />
          <DatePicker v-model:value="payDate" />
          <FileUpload :value="payVoucherUrl ? [payVoucherUrl] : []" :max-number="1" @update:value="onVoucher" />
          <Button type="primary" @click="onRecordPay">登记支付</Button>
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
