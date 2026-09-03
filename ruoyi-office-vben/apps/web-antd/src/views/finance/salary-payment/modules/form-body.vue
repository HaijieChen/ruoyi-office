<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { Button, Form, Input, InputNumber, Select, Space, message } from 'ant-design-vue';
import { createAndStartSalaryPayment, getSalaryPayment, resubmitSalaryPayment } from '#/api/finance/salary-payment';
import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';
import { getSimpleDeptList } from '#/api/system/dept';
import { FileUpload } from '#/components/upload';
defineOptions({ name: 'FinanceSalaryPaymentFormBody' });
const emit = defineEmits(['predictChange', 'success']);
const form = ref({ id: undefined, paymentTiming: 'IMMEDIATE', periodLabel: undefined, currency: 'CNY', specialNote: undefined, evidenceFileUrls: [], lines: [{}] });
const companyOptions = ref([]);
const accountOptionsByCompany = ref({});
const submitting = ref(false);
const isResubmit = computed(function () { return !!form.value.id; });
const total = computed(function () {
  return form.value.lines.reduce(function (s, l) {
    return s + Number(l.netSalaryAmount || 0) + Number(l.personalTaxAmount || 0) + Number(l.socialInsuranceAmount || 0) + Number(l.housingFundAmount || 0);
  }, 0);
});
onMounted(async function () {
  try {
    const list = await getSimpleDeptList();
    companyOptions.value = (list || []).filter(function (d) { return String(d.orgType) === '1'; }).map(function (d) { return { label: d.name, value: d.id }; });
  } catch (e) { companyOptions.value = []; }
});
async function loadAccounts(companyId) {
  if (!companyId) return [];
  if (accountOptionsByCompany.value[companyId]) return accountOptionsByCompany.value[companyId];
  try {
    const list = await getCompanyBankAccountSimpleList(companyId);
    const opts = (list || []).map(function (a) {
      return { label: a.accountName + ' / ' + a.bankName + ' / ' + (a.accountNoMasked || ''), value: a.id };
    });
    accountOptionsByCompany.value = { ...accountOptionsByCompany.value, [companyId]: opts };
    return opts;
  } catch (e) {
    accountOptionsByCompany.value = { ...accountOptionsByCompany.value, [companyId]: [] };
    return [];
  }
}
function accountsFor(companyId) {
  return accountOptionsByCompany.value[companyId] || [];
}
function onCompanyChange(line) {
  line.companyBankAccountId = undefined;
  if (line.entityCompanyDeptId) loadAccounts(line.entityCompanyDeptId);
}
function addLine() { form.value.lines.push({}); }
function removeLine(idx) { form.value.lines.splice(idx, 1); if (form.value.lines.length === 0) form.value.lines.push({}); }
function parseEvidence(raw) {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try { const j = JSON.parse(raw); return Array.isArray(j) ? j : []; } catch (e) { return raw ? [raw] : []; }
}
function getPredictVariables() { return { periodLabel: form.value.periodLabel, currency: form.value.currency }; }
async function reset(opts) {
  const copyId = Number(opts && opts.copyFromBusinessKey);
  if (opts && opts.id) {
    const detail = await getSalaryPayment(opts.id);
    form.value = {
      id: detail.id,
      paymentTiming: detail.paymentTiming || 'IMMEDIATE',
      periodLabel: detail.periodLabel,
      currency: detail.currency || 'CNY',
      specialNote: detail.specialNote,
      evidenceFileUrls: parseEvidence(detail.evidenceFileUrls),
      lines: (detail.salaryLines || []).map(function (l) {
        if (l.entityCompanyDeptId) loadAccounts(l.entityCompanyDeptId);
        return { entityCompanyDeptId: l.entityCompanyDeptId, companyBankAccountId: l.companyBankAccountId, netSalaryAmount: Number(l.netSalaryAmount || 0), personalTaxAmount: Number(l.personalTaxAmount || 0), socialInsuranceAmount: Number(l.socialInsuranceAmount || 0), housingFundAmount: Number(l.housingFundAmount || 0) };
      }),
    };
    if (!form.value.lines.length) form.value.lines = [{}];
  } else if (Number.isFinite(copyId) && copyId > 0) {
    const detail = await getSalaryPayment(copyId);
    form.value = {
      id: undefined,
      paymentTiming: detail.paymentTiming || 'IMMEDIATE',
      periodLabel: detail.periodLabel,
      currency: detail.currency || 'CNY',
      specialNote: detail.specialNote,
      evidenceFileUrls: parseEvidence(detail.evidenceFileUrls),
      lines: (detail.salaryLines || []).map(function (l) {
        if (l.entityCompanyDeptId) loadAccounts(l.entityCompanyDeptId);
        return { entityCompanyDeptId: l.entityCompanyDeptId, companyBankAccountId: l.companyBankAccountId, netSalaryAmount: Number(l.netSalaryAmount || 0), personalTaxAmount: Number(l.personalTaxAmount || 0), socialInsuranceAmount: Number(l.socialInsuranceAmount || 0), housingFundAmount: Number(l.housingFundAmount || 0) };
      }),
    };
    if (!form.value.lines.length) form.value.lines = [{}];
  } else {
    form.value = { id: undefined, paymentTiming: 'IMMEDIATE', periodLabel: undefined, currency: 'CNY', specialNote: undefined, evidenceFileUrls: [], lines: [{}] };
  }
  emit('predictChange', getPredictVariables());
}
async function submit(ctx?: { startCompanyDeptId?: number; startDeptId?: number }) {
  if (!form.value.periodLabel) { message.error('请填写薪资期间'); throw new Error('period'); }
  if (!form.value.lines.every(function (l) { return l.entityCompanyDeptId; })) { message.error('请为每行选择主体公司'); throw new Error('company'); }
  if (!form.value.lines.every(function (l) { return l.companyBankAccountId; })) { message.error('请为每行选择公司银行账户'); throw new Error('account'); }
  const payload = {
    paymentTiming: form.value.paymentTiming || 'IMMEDIATE',
    periodLabel: form.value.periodLabel,
    currency: form.value.currency || 'CNY',
    specialNote: form.value.specialNote,
    evidenceFileUrls: form.value.evidenceFileUrls,
    lines: form.value.lines.map(function (l) {
      return { entityCompanyDeptId: l.entityCompanyDeptId, companyBankAccountId: l.companyBankAccountId, netSalaryAmount: Number(l.netSalaryAmount || 0), personalTaxAmount: Number(l.personalTaxAmount || 0), socialInsuranceAmount: Number(l.socialInsuranceAmount || 0), housingFundAmount: Number(l.housingFundAmount || 0) };
    }),
    startCompanyDeptId: ctx?.startCompanyDeptId,
      startDeptId: ctx?.startDeptId,
  };
  submitting.value = true;
  try {
    if (isResubmit.value && form.value.id) { await resubmitSalaryPayment(form.value.id, payload); message.success('已重提薪资付款申请'); }
    else { await createAndStartSalaryPayment(payload); message.success('已提交薪资付款申请'); }
    emit('success');
  } finally { submitting.value = false; }
}
function onAttach(v) { form.value.evidenceFileUrls = Array.isArray(v) ? v : (v ? [v] : []); }
defineExpose({ reset: reset, submit: submit, getPredictVariables: getPredictVariables, submitting: submitting });
</script>

<template>
  <Form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
    <Form.Item label="薪资期间" required><Input v-model:value="form.periodLabel" placeholder="如 2026-07" /></Form.Item>
    <Form.Item label="支付时效" required>
      <Select v-model:value="form.paymentTiming" :options="[{ label: '即时', value: 'IMMEDIATE' }, { label: '月底', value: 'MONTH_END' }, { label: '通知', value: 'ON_NOTICE' }]" />
    </Form.Item>
    <Form.Item label="币种" required>
      <Select v-model:value="form.currency" :options="[{ label: 'CNY', value: 'CNY' }, { label: 'USD', value: 'USD' }, { label: 'HKD', value: 'HKD' }]" />
    </Form.Item>
    <Form.Item label="明细" required>
      <div v-for="(line, idx) in form.lines" :key="idx" class="mb-2 rounded border p-2">
        <Space wrap>
          <Select v-model:value="line.entityCompanyDeptId" style="width: 180px" :options="companyOptions" placeholder="主体公司" show-search option-filter-prop="label" @change="onCompanyChange(line)" />
          <Select v-model:value="line.companyBankAccountId" style="width: 240px" :options="accountsFor(line.entityCompanyDeptId)" :disabled="!line.entityCompanyDeptId" placeholder="公司账户" show-search option-filter-prop="label" />
          <InputNumber v-model:value="line.netSalaryAmount" :min="0" :precision="2" placeholder="实发" />
          <InputNumber v-model:value="line.personalTaxAmount" :min="0" :precision="2" placeholder="个税" />
          <InputNumber v-model:value="line.socialInsuranceAmount" :min="0" :precision="2" placeholder="社保" />
          <InputNumber v-model:value="line.housingFundAmount" :min="0" :precision="2" placeholder="公积金（选填）" />
          <Button danger type="link" @click="removeLine(idx)">删</Button>
        </Space>
      </div>
      <Button type="dashed" block @click="addLine">加一行</Button>
      <div class="mt-2">行合计（自动）：{{ total.toFixed(2) }}</div>
    </Form.Item>
    <Form.Item label="依据附件">
      <FileUpload :value="form.evidenceFileUrls || []" :max-number="10" :max-size="20" :multiple="true" help-text="员工明细附件受控可选" @update:value="onAttach" />
    </Form.Item>
    <Form.Item label="特殊说明"><Input.TextArea v-model:value="form.specialNote" :rows="2" /></Form.Item>
  </Form>
</template>
