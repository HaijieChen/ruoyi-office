<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  message,
} from 'ant-design-vue';

import {
  createAndStartSalaryPayment,
  getSalaryPayment,
  resubmitSalaryPayment,
} from '#/api/finance/salary-payment';
import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';
import { getSimpleDeptList } from '#/api/system/dept';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinanceSalaryPaymentForm' });

const emit = defineEmits(['success']);

interface Line {
  entityCompanyDeptId?: number;
  companyBankAccountId?: number;
  netSalaryAmount?: number;
  personalTaxAmount?: number;
  socialInsuranceAmount?: number;
  housingFundAmount?: number;
}

const form = ref<{
  id?: number;
  paymentTiming?: string;
  periodLabel?: string;
  currency?: string;
  specialNote?: string;
  evidenceFileUrls?: string[];
  lines: Line[];
}>({
  currency: 'CNY',
  paymentTiming: 'IMMEDIATE',
  lines: [{}],
});

const companyOptions = ref<{ label: string; value: number }[]>([]);
const accountOptionsByCompany = ref<Record<number, { label: string; value: number }[]>>({});
const isResubmit = computed(() => !!form.value.id);
const title = computed(() =>
  isResubmit.value ? '驳回后重提薪资付款' : '新建薪资付款申请',
);

onMounted(async () => {
  try {
    const list = await getSimpleDeptList();
    companyOptions.value = (list || [])
      .filter((d: any) => String(d.orgType) === '1')
      .map((d: any) => ({ label: d.name, value: d.id }));
  } catch {
    companyOptions.value = [];
  }
});

async function loadAccounts(companyId?: number) {
  if (!companyId) return [];
  if (accountOptionsByCompany.value[companyId]) {
    return accountOptionsByCompany.value[companyId];
  }
  try {
    const list = await getCompanyBankAccountSimpleList(companyId);
    const opts = (list || []).map((a) => ({
      label: `${a.accountName} / ${a.bankName} / ${a.accountNoMasked || ''}`,
      value: a.id,
    }));
    accountOptionsByCompany.value = { ...accountOptionsByCompany.value, [companyId]: opts };
    return opts;
  } catch {
    accountOptionsByCompany.value = { ...accountOptionsByCompany.value, [companyId]: [] };
    return [];
  }
}
function accountsFor(companyId?: number) {
  return companyId ? accountOptionsByCompany.value[companyId] || [] : [];
}
function onCompanyChange(line: Line) {
  line.companyBankAccountId = undefined;
  if (line.entityCompanyDeptId) void loadAccounts(line.entityCompanyDeptId);
}
function addLine() {
  form.value.lines.push({});
}

function removeLine(idx: number) {
  form.value.lines.splice(idx, 1);
  if (form.value.lines.length === 0) form.value.lines.push({});
}

const total = computed(() =>
  form.value.lines.reduce((s, l) => {
    return (
      s +
      Number(l.netSalaryAmount || 0) +
      Number(l.personalTaxAmount || 0) +
      Number(l.socialInsuranceAmount || 0) +
      Number(l.housingFundAmount || 0)
    );
  }, 0),
);

function parseEvidence(raw?: string | string[]): string[] {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try {
    const j = JSON.parse(raw);
    return Array.isArray(j) ? j : [];
  } catch {
    return raw ? [raw] : [];
  }
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number }>() || {};
    if (data.id) {
      const detail = await getSalaryPayment(data.id);
      form.value = {
        id: detail.id,
        paymentTiming: detail.paymentTiming || 'IMMEDIATE',
        periodLabel: (detail as any).periodLabel,
        currency: detail.currency || 'CNY',
        specialNote: detail.specialNote,
        evidenceFileUrls: parseEvidence(detail.evidenceFileUrls as any),
        lines: ((detail as any).salaryLines || []).map((l: any) => {
          if (l.entityCompanyDeptId) void loadAccounts(l.entityCompanyDeptId);
          return {
            entityCompanyDeptId: l.entityCompanyDeptId,
            companyBankAccountId: l.companyBankAccountId,
            netSalaryAmount: Number(l.netSalaryAmount || 0),
            personalTaxAmount: Number(l.personalTaxAmount || 0),
            socialInsuranceAmount: Number(l.socialInsuranceAmount || 0),
            housingFundAmount: Number(l.housingFundAmount || 0),
          };
        }),
      };
      if (!form.value.lines.length) form.value.lines = [{}];
    } else {
      form.value = {
        currency: 'CNY',
        paymentTiming: 'IMMEDIATE',
        lines: [{}],
      };
    }
  },
  async onConfirm() {
    if (!form.value.periodLabel) {
      message.error('请填写薪资期间');
      return;
    }
    if (!form.value.lines.every((l) => l.entityCompanyDeptId)) {
      message.error('请为每行选择主体公司');
      return;
    }
    if (!form.value.lines.every((l) => l.companyBankAccountId)) {
      message.error('请为每行选择公司银行账户');
      return;
    }
    const payload = {
      paymentTiming: form.value.paymentTiming || 'IMMEDIATE',
      periodLabel: form.value.periodLabel!,
      currency: form.value.currency || 'CNY',
      specialNote: form.value.specialNote,
      evidenceFileUrls: form.value.evidenceFileUrls,
      lines: form.value.lines.map((l) => ({
        entityCompanyDeptId: l.entityCompanyDeptId!,
        companyBankAccountId: l.companyBankAccountId!,
        netSalaryAmount: Number(l.netSalaryAmount || 0),
        personalTaxAmount: Number(l.personalTaxAmount || 0),
        socialInsuranceAmount: Number(l.socialInsuranceAmount || 0),
        housingFundAmount: Number(l.housingFundAmount || 0),
      })),
    };
    modalApi.lock();
    try {
      if (isResubmit.value && form.value.id) {
        await resubmitSalaryPayment(form.value.id, payload);
        message.success('已重提薪资付款申请');
      } else {
        await createAndStartSalaryPayment(payload);
        message.success('已提交薪资付款申请');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  onClosed() {
    form.value = {
      currency: 'CNY',
      paymentTiming: 'IMMEDIATE',
      lines: [{}],
    };
  },
});
</script>

<template>
  <Modal :title="title" class="w-[820px]">
    <Form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <Form.Item label="薪资期间" required>
        <Input v-model:value="form.periodLabel" placeholder="如 2026-07" />
      </Form.Item>
      <Form.Item label="支付时效" required>
        <Select
          v-model:value="form.paymentTiming"
          :options="[
            { label: '即时', value: 'IMMEDIATE' },
            { label: '月底', value: 'MONTH_END' },
            { label: '通知', value: 'ON_NOTICE' },
          ]"
        />
      </Form.Item>
      <Form.Item label="币种" required>
        <Select
          v-model:value="form.currency"
          :options="[
            { label: 'CNY', value: 'CNY' },
            { label: 'USD', value: 'USD' },
            { label: 'HKD', value: 'HKD' },
          ]"
        />
      </Form.Item>
      <Form.Item label="明细" required>
        <div
          v-for="(line, idx) in form.lines"
          :key="idx"
          class="mb-2 rounded border p-2"
        >
          <Space wrap>
            <Select
              v-model:value="line.entityCompanyDeptId"
              style="width: 180px"
              :options="companyOptions"
              placeholder="主体公司"
              show-search
              option-filter-prop="label"
              @change="onCompanyChange(line)"
            />
            <Select
              v-model:value="line.companyBankAccountId"
              style="width: 240px"
              :options="accountsFor(line.entityCompanyDeptId)"
              :disabled="!line.entityCompanyDeptId"
              placeholder="公司账户"
              show-search
              option-filter-prop="label"
            />
            <InputNumber
              v-model:value="line.netSalaryAmount"
              :min="0"
              :precision="2"
              placeholder="实发"
            />
            <InputNumber
              v-model:value="line.personalTaxAmount"
              :min="0"
              :precision="2"
              placeholder="个税"
            />
            <InputNumber
              v-model:value="line.socialInsuranceAmount"
              :min="0"
              :precision="2"
              placeholder="社保"
            />
            <InputNumber
              v-model:value="line.housingFundAmount"
              :min="0"
              :precision="2"
              placeholder="公积金（选填）"
            />
            <Button danger type="link" @click="removeLine(idx)">删</Button>
          </Space>
        </div>
        <Button type="dashed" block @click="addLine">加一行</Button>
        <div class="mt-2">行合计（自动）：{{ total.toFixed(2) }}</div>
      </Form.Item>
      <Form.Item label="依据附件">
        <FileUpload
          :value="form.evidenceFileUrls || []"
          :max-number="10"
          :max-size="20"
          :multiple="true"
          help-text="员工明细附件受控可选"
          @update:value="
            (v) =>
              (form.evidenceFileUrls = Array.isArray(v) ? v : v ? [v] : [])
          "
        />
      </Form.Item>
      <Form.Item label="特殊说明">
        <Input.TextArea v-model:value="form.specialNote" :rows="2" />
      </Form.Item>
    </Form>
  </Modal>
</template>
