<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';
import type { DefaultOptionType, SelectValue } from 'ant-design-vue/es/select';

import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

import { computed, ref, watch } from 'vue';

import { getDictOptions } from '@vben/hooks';

import { Form, Input, InputNumber, message, Select } from 'ant-design-vue';

import { listSelectableContractsForBo } from '#/api/finance/contract-application';
import { getCustomerCompanySimpleList } from '#/api/finance/customer-company';
import {
  createAndStartPaymentApplication,
  getCumulativePaid,
  getPaymentApplication,
  listSelectableLeaseContracts,
  listSelectablePurchaseInstances,
  resubmitPaymentApplication,
} from '#/api/finance/payment-application';
import { getSimpleCompanyList } from '#/api/system/dept';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinancePaymentApplicationFormBody' });

const CURRENCY_OPTIONS = [
  { label: '人民币 CNY', value: 'CNY' },
  { label: '美元 USD', value: 'USD' },
  { label: '港币 HKD', value: 'HKD' },
];

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

interface FormData {
  id?: number;
  paymentTiming?: string;
  paymentReason?: string;
  purchaseProcessInstanceId?: string;
  leaseContractApplicationId?: number;
  relatedContractApplicationId?: number;
  entityCompanyDeptId?: number;
  payeeCompanyId?: number;
  payeeBankName?: string;
  payeeBankAccount?: string;
  applyAmount?: number;
  currency?: string;
  businessSettlementTerm?: string;
  payMethod?: string;
  costProject?: string;
  evidenceFileUrls?: string[];
  specialNote?: string;
}

const formRef = ref();
const formData = ref<FormData>({
  currency: 'CNY',
  paymentTiming: 'IMMEDIATE',
});
const mode = ref<'create' | 'resubmit'>('create');
const supplierOptions = ref<
  { bankAccount?: string; bankName?: string; label: string; value: number }[]
>([]);
const companyOptions = ref<
  { functionalCurrency?: string; label: string; value: number }[]
>([]);
const purchaseOptions = ref<{ label: string; value: string }[]>([]);
const leaseOptions = ref<{ label: string; value: number }[]>([]);
const relatedContractOptions = ref<{ label: string; value: number }[]>([]);
const cumulativePaid = ref(0);
const submitting = ref(false);
/** 用户是否手动改过币种（切换主体时不再覆盖） */
const currencyTouched = ref(false);

const isPurchase = computed(() => formData.value.paymentReason === 'PURCHASE');
const isLease = computed(() => formData.value.paymentReason === 'LEASE');
const isBusiness = computed(() => formData.value.paymentReason === 'BUSINESS');
const cumulativeAfter = computed(() => {
  const amt = Number(formData.value.applyAmount || 0);
  return Number(cumulativePaid.value) + amt;
});

const rules: Record<string, Rule[]> = {
  paymentTiming: [{ required: true, message: '请选择支付时效' }],
  paymentReason: [{ required: true, message: '请选择付款事由' }],
  entityCompanyDeptId: [{ required: true, message: '请选择主体公司' }],
  payeeCompanyId: [{ required: true, message: '请选择收款方' }],
  applyAmount: [{ required: true, message: '请输入金额' }],
  currency: [{ required: true, message: '请选择币种' }],
  businessSettlementTerm: [{ required: true, message: '请填写账期' }],
  payMethod: [{ required: true, message: '请选择支付方式' }],
  costProject: [{ required: true, message: '请选择费用项目' }],
  evidenceFileUrls: [
    {
      required: true,
      validator: async (_rule, value: string[] | undefined) => {
        if (!value || !value.some((u) => String(u || '').trim())) {
          throw new Error('请上传至少一份付款依据');
        }
      },
    },
  ],
};

async function loadSuppliers() {
  const list = (await getCustomerCompanySimpleList('SUPPLIER')) || [];
  supplierOptions.value = list.map((c) => ({
    label: `${c.name}（${c.taxNo || '-'}）`,
    value: c.id,
    bankName: c.bankName,
    bankAccount: c.bankAccount,
  }));
}

async function loadCompanies() {
  const list = (await getSimpleCompanyList()) || [];
  companyOptions.value = list.map((c) => ({
    label: c.name,
    value: c.id as number,
    functionalCurrency: c.functionalCurrency || 'CNY',
  }));
}

function onEntityCompanyChange(value: SelectValue) {
  let id = Number.NaN;
  if (typeof value === 'number') {
    id = value;
  } else if (value !== null && value !== undefined && value !== '') {
    id = Number(value);
  }
  if (!Number.isFinite(id)) {
    return;
  }
  const opt = companyOptions.value.find((o) => o.value === id);
  // 切换主体：若用户未手改币种，默认带出公司本位币（可再改）
  if (opt && !currencyTouched.value) {
    const fc = (opt.functionalCurrency || 'CNY').toUpperCase();
    formData.value.currency = ['CNY', 'USD', 'HKD'].includes(fc) ? fc : 'CNY';
  }
}

function onCurrencyChange() {
  currencyTouched.value = true;
}

async function loadPurchase() {
  const list = (await listSelectablePurchaseInstances()) || [];
  purchaseOptions.value = list.map((p) => ({
    label: p.summary || p.name || p.processInstanceId,
    value: p.processInstanceId,
  }));
}

async function loadLease() {
  const list = (await listSelectableLeaseContracts()) || [];
  leaseOptions.value = (list as any[]).map((c) => ({
    label: `${c.applicationNo || c.id} ${c.fileName || c.counterpartyName || ''}`,
    value: c.id,
  }));
}

async function loadRelatedContracts() {
  const list = (await listSelectableContractsForBo()) || [];
  relatedContractOptions.value = list.map((c) => ({
    label: `${c.applicationNo || c.id} ${c.fileName || c.counterpartyName || ''}`,
    value: c.id,
  }));
}

function onEvidenceUpload(val: string | string[]) {
  let urls: string[] = [];
  if (Array.isArray(val)) {
    urls = val;
  } else if (val) {
    urls = [val];
  }
  formData.value.evidenceFileUrls = urls.filter(Boolean);
}

function dictOptions(dictType: string): DefaultOptionType[] {
  return getDictOptions(dictType).map((d) => ({
    label: d.label,
    value: d.value as number | string,
  }));
}

function onPayeeChange(value: SelectValue) {
  let id = Number.NaN;
  if (typeof value === 'number') {
    id = value;
  } else if (value !== null && value !== undefined && value !== '') {
    id = Number(value);
  }
  if (!Number.isFinite(id)) {
    return;
  }
  const opt = supplierOptions.value.find((o) => o.value === id);
  if (opt) {
    formData.value.payeeBankName = opt.bankName;
    formData.value.payeeBankAccount = opt.bankAccount;
  }
  void refreshCumulative(id);
}

async function refreshCumulative(payeeId?: number) {
  if (!payeeId) {
    cumulativePaid.value = 0;
    return;
  }
  try {
    const res = await getCumulativePaid(payeeId);
    cumulativePaid.value = Number(res?.paidSum ?? 0);
  } catch {
    cumulativePaid.value = 0;
  }
}

function getPredictVariables(): Record<string, unknown> {
  const vars: Record<string, unknown> = {};
  const amt = formData.value.applyAmount;
  if (amt !== null && amt !== undefined && Number.isFinite(Number(amt))) {
    vars.applyAmount = Number(amt);
  }
  if (formData.value.paymentReason) {
    vars.paymentReason = formData.value.paymentReason;
  }
  return vars;
}

function emitPredict() {
  emit('predictChange', getPredictVariables());
}

watch(
  () => formData.value.paymentReason,
  async (r) => {
    if (r === 'PURCHASE') await loadPurchase();
    if (r === 'LEASE') await loadLease();
    if (r === 'BUSINESS') await loadRelatedContracts();
    if (r !== 'BUSINESS') {
      formData.value.relatedContractApplicationId = undefined;
    }
    emitPredict();
  },
);

watch(
  () => formData.value.applyAmount,
  () => {
    emitPredict();
  },
);

function parseEvidenceUrls(raw?: string): string[] {
  try {
    const arr = JSON.parse(raw || '[]');
    return Array.isArray(arr) ? arr.filter(Boolean) : [];
  } catch {
    const s = raw || '';
    return s
      ? s
          .split(/[,，\n]/)
          .map((x) => x.trim())
          .filter(Boolean)
      : [];
  }
}

/** 初始化（壳内 mount 或 Modal open） */
async function reset(opts?: { id?: number; mode?: string }) {
  await Promise.all([loadSuppliers(), loadCompanies()]);
  mode.value = opts?.mode === 'resubmit' ? 'resubmit' : 'create';
  currencyTouched.value = false;
  if (opts?.id) {
    const detail = await getPaymentApplication(opts.id);
    formData.value = {
      id: detail.id,
      paymentTiming: detail.paymentTiming,
      paymentReason: detail.paymentReason,
      purchaseProcessInstanceId: detail.purchaseProcessInstanceId,
      leaseContractApplicationId: detail.leaseContractApplicationId,
      relatedContractApplicationId: detail.relatedContractApplicationId,
      entityCompanyDeptId: detail.entityCompanyDeptId,
      payeeCompanyId: detail.payeeCompanyId,
      payeeBankName: detail.payeeBankName,
      payeeBankAccount: detail.payeeBankAccount,
      applyAmount: detail.applyAmount,
      currency: detail.currency || 'CNY',
      businessSettlementTerm: detail.businessSettlementTerm,
      payMethod: detail.payMethod,
      costProject: detail.costProject,
      evidenceFileUrls: parseEvidenceUrls(detail.evidenceFileUrls),
      specialNote: detail.specialNote,
    };
    // 历史驳回重提：主体为空时须补选；币种若已有则视为已确认
    if (detail.entityCompanyDeptId) {
      currencyTouched.value = true;
    }
    // 历史主体不在启用列表时补一条选项
    if (
      detail.entityCompanyDeptId != null &&
      !companyOptions.value.some((o) => o.value === detail.entityCompanyDeptId)
    ) {
      companyOptions.value = [
        {
          label: detail.entityCompanyName || `公司 #${detail.entityCompanyDeptId}`,
          value: detail.entityCompanyDeptId,
          functionalCurrency: detail.currency || 'CNY',
        },
        ...companyOptions.value,
      ];
    }
    await refreshCumulative(detail.payeeCompanyId);
    if (detail.paymentReason === 'PURCHASE') await loadPurchase();
    if (detail.paymentReason === 'LEASE') await loadLease();
    if (detail.paymentReason === 'BUSINESS') await loadRelatedContracts();
  } else {
    formData.value = {
      currency: 'CNY',
      paymentTiming: 'IMMEDIATE',
      evidenceFileUrls: [],
    };
    cumulativePaid.value = 0;
  }
  emitPredict();
}

interface SubmitContext {
  startUserSelectAssignees?: Record<string, number[]>;
}

async function submit(ctx?: SubmitContext): Promise<void> {
  if (isPurchase.value && !formData.value.purchaseProcessInstanceId) {
    message.error('采购付款须选择已通过的采购实例');
    throw new Error('validation');
  }
  if (isLease.value && !formData.value.leaseContractApplicationId) {
    message.error('房屋租赁须选择已通过的租赁合同');
    throw new Error('validation');
  }
  await formRef.value?.validate();
  submitting.value = true;
  try {
    const urls = (formData.value.evidenceFileUrls || [])
      .map((s) => String(s).trim())
      .filter(Boolean);
    if (!formData.value.entityCompanyDeptId) {
      message.error('请选择主体公司');
      throw new Error('validation');
    }
    const currency = (formData.value.currency || '').toUpperCase();
    if (!['CNY', 'USD', 'HKD'].includes(currency)) {
      message.error('币种仅支持 CNY/USD/HKD');
      throw new Error('validation');
    }
    const payload: FinancePaymentApplicationApi.CreateAndStartRequest = {
      paymentTiming: formData.value.paymentTiming!,
      paymentReason: formData.value.paymentReason!,
      purchaseProcessInstanceId: isPurchase.value
        ? formData.value.purchaseProcessInstanceId
        : undefined,
      leaseContractApplicationId: isLease.value
        ? formData.value.leaseContractApplicationId
        : undefined,
      relatedContractApplicationId: isBusiness.value
        ? formData.value.relatedContractApplicationId
        : undefined,
      entityCompanyDeptId: formData.value.entityCompanyDeptId,
      payeeCompanyId: formData.value.payeeCompanyId!,
      payeeBankName: formData.value.payeeBankName,
      payeeBankAccount: formData.value.payeeBankAccount,
      applyAmount: formData.value.applyAmount!,
      currency,
      businessSettlementTerm: formData.value.businessSettlementTerm!,
      payMethod: formData.value.payMethod!,
      costProject: formData.value.costProject!,
      evidenceFileUrls: urls,
      specialNote: formData.value.specialNote,
      startUserSelectAssignees: ctx?.startUserSelectAssignees,
    };
    if (mode.value === 'resubmit' && formData.value.id) {
      await resubmitPaymentApplication(formData.value.id, payload);
      message.success('重提成功');
    } else {
      await createAndStartPaymentApplication(payload);
      message.success('提交成功');
    }
    emit('success');
  } finally {
    submitting.value = false;
  }
}

defineExpose({
  reset,
  submit,
  getPredictVariables,
  submitting,
});
</script>

<template>
  <div>
    <div class="mb-2 text-sm text-gray-500">
      主体公司可选任意启用公司；币种默认公司本位币，提交前可改。累计 = 已支付
      {{ cumulativePaid }} + 本次 → {{ cumulativeAfter }}
    </div>
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <Form.Item label="主体公司" name="entityCompanyDeptId" required>
        <Select
          v-model:value="formData.entityCompanyDeptId"
          :options="companyOptions"
          show-search
          option-filter-prop="label"
          placeholder="请选择业务主体公司"
          @change="onEntityCompanyChange"
        />
      </Form.Item>
      <Form.Item label="支付时效" name="paymentTiming" required>
        <Select
          v-model:value="formData.paymentTiming"
          :options="dictOptions('finance_payment_timing')"
          placeholder="即时/月底/通知"
        />
      </Form.Item>
      <Form.Item label="付款事由" name="paymentReason" required>
        <Select
          v-model:value="formData.paymentReason"
          :options="dictOptions('finance_payment_reason')"
        />
      </Form.Item>
      <Form.Item v-if="isPurchase" label="采购实例" required>
        <Select
          v-model:value="formData.purchaseProcessInstanceId"
          :options="purchaseOptions"
          show-search
          allow-clear
          placeholder="已通过采购流程"
        />
      </Form.Item>
      <Form.Item v-if="isLease" label="租赁合同" required>
        <Select
          v-model:value="formData.leaseContractApplicationId"
          :options="leaseOptions"
          show-search
          allow-clear
        />
      </Form.Item>
      <Form.Item v-if="isBusiness" label="关联合同（可选）">
        <Select
          v-model:value="formData.relatedContractApplicationId"
          :options="relatedContractOptions"
          show-search
          allow-clear
          placeholder="本人已通过合同，带出结算方式"
        />
      </Form.Item>
      <Form.Item label="收款方" name="payeeCompanyId" required>
        <Select
          v-model:value="formData.payeeCompanyId"
          :options="supplierOptions"
          show-search
          @change="onPayeeChange"
        />
      </Form.Item>
      <Form.Item label="开户行">
        <Input v-model:value="formData.payeeBankName" />
      </Form.Item>
      <Form.Item label="银行账号">
        <Input v-model:value="formData.payeeBankAccount" />
      </Form.Item>
      <Form.Item label="申请金额" name="applyAmount" required>
        <InputNumber
          v-model:value="formData.applyAmount"
          :min="0.01"
          class="w-full"
        />
      </Form.Item>
      <Form.Item label="币种" name="currency" required>
        <Select
          v-model:value="formData.currency"
          :options="CURRENCY_OPTIONS"
          placeholder="CNY/USD/HKD"
          @change="onCurrencyChange"
        />
      </Form.Item>
      <Form.Item label="账期" name="businessSettlementTerm" required>
        <Input v-model:value="formData.businessSettlementTerm" />
      </Form.Item>
      <Form.Item label="支付方式" name="payMethod" required>
        <Select
          v-model:value="formData.payMethod"
          :options="dictOptions('finance_pay_method')"
        />
      </Form.Item>
      <Form.Item label="费用项目" name="costProject" required>
        <Select
          v-model:value="formData.costProject"
          :options="dictOptions('finance_cost_project')"
        />
      </Form.Item>
      <Form.Item label="付款依据" name="evidenceFileUrls" required>
        <FileUpload
          :value="formData.evidenceFileUrls"
          :max-number="10"
          :max-size="20"
          :multiple="true"
          help-text="上传回单/合同等依据，至少一份"
          @update:value="onEvidenceUpload"
        />
      </Form.Item>
      <Form.Item label="特殊说明">
        <Input.TextArea v-model:value="formData.specialNote" :rows="2" />
      </Form.Item>
    </Form>
  </div>
</template>
