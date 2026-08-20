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
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinanceExpenseReimbursementDetail' });

const props = defineProps<{ id?: string }>();
const { query } = useRoute();
const loading = ref(false);
const bill = ref<FinanceExpenseApi.Bill>();
const approvedAmount = ref<number>();
const financeComment = ref('');
const payDate = ref<Dayjs>();
const payVoucherUrl = ref('');
const companyBankAccountId = ref<number>();
const accountOptions = ref<{ label: string; value: number }[]>([]);

const queryId = computed(() => Number(props.id || query.id));

async function load() {
  loading.value = true;
  try {
    bill.value = await getExpenseReimbursement(queryId.value);
    approvedAmount.value = Number(bill.value?.applyAmount || 0);
    const page = await getCompanyBankAccountPage({
      pageNo: 1,
      pageSize: 100,
      status: 0,
    });
    accountOptions.value = (page?.list || []).map((a) => ({
      value: a.id,
      label: `${a.accountName} ${a.accountNoMasked || ""}`,
    }));
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

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Spin :spinning="loading">
      <div v-if="bill" class="mx-auto max-w-3xl p-4 print:max-w-none">
        <div class="mb-2 text-right print:hidden">
          <Button @click="window.print()">打印</Button>
        </div>
        <Descriptions bordered :column="2" size="small">
          <Descriptions.Item label="标题">{{ bill.processTitle }}</Descriptions.Item>
          <Descriptions.Item label="状态">{{ bill.status }}</Descriptions.Item>
          <Descriptions.Item label="期间">{{ bill.periodLabel }}</Descriptions.Item>
          <Descriptions.Item v-if="bill.entityCompanyName" label="主体公司">{{ bill.entityCompanyName }}</Descriptions.Item>
          <Descriptions.Item label="代票">{{ bill.proxyTicket ? "是" : "否" }}</Descriptions.Item>
          <Descriptions.Item label="收款户名">{{ bill.payeeAccountName }}</Descriptions.Item>
          <Descriptions.Item label="收款账号">{{ bill.payeeAccountNo }}</Descriptions.Item>
          <Descriptions.Item label="申请金额">{{ bill.applyAmount }}</Descriptions.Item>
          <Descriptions.Item label="实报金额">{{ bill.approvedAmount }}</Descriptions.Item>
        </Descriptions>
        <div class="mt-3 text-sm">
          <div v-for="(line, i) in bill.lines || []" :key="i">
            {{ line.feeDate }} ·
            <span :class="bill.proxyTicket ? 'print:hidden' : ''">{{ line.category }}{{ line.subItem ? '/' + line.subItem : '' }}</span>
            <span v-if="bill.proxyTicket"> · 发票 {{ line.invoiceType || '-' }}</span>
            · {{ line.amount }}{{ line.invoiceNo ? ' · 票号 ' + line.invoiceNo : '' }}
            {{ line.stayCityTier === 'T1' ? ' · 北上广深' : line.stayCityTier === 'OTHER' ? ' · 其他城市' : '' }}
            {{ line.overLimitReason ? ` · 超标：${line.overLimitReason}` : "" }}
            {{ line.remark ? ` · ${line.remark}` : "" }}
          </div>
        </div>
        <div v-if="bill.status === 'PENDING'" class="mt-6 space-y-2">
          <div class="font-medium">财务实报</div>
          <InputNumber v-model:value="approvedAmount" :min="0.01" :precision="2" class="w-40" />
          <Input v-model:value="financeComment" placeholder="审核意见" />
          <Button type="primary" @click="onApprove">提交实报金额</Button>
        </div>
        <div v-if="bill.status === 'WAIT_PAY'" class="mt-6 space-y-2">
          <div class="font-medium">出纳登记</div>
          <Select v-model:value="companyBankAccountId" class="w-72" :options="accountOptions" placeholder="公司银行账户" />
          <DatePicker v-model:value="payDate" />
          <FileUpload :value="payVoucherUrl ? [payVoucherUrl] : []" :max-number="1" @update:value="onVoucher" />
          <Button type="primary" @click="onRecordPay">登记支付</Button>
        </div>
      </div>
    </Spin>
  </Page>
</template>
