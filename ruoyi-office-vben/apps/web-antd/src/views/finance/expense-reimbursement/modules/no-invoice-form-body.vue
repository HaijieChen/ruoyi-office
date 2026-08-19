<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, onMounted, ref } from 'vue';

import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getOutingPage } from '#/api/bpm/oa/outing';
import { getTripPage } from '#/api/bpm/oa/trip';
import { createNoInvoiceExpense } from '#/api/finance/expense-reimbursement';

defineOptions({ name: 'FinanceExpenseNoInvoiceFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const tripOptions = ref<{ label: string; value: string; type: 'TRIP' }[]>([]);
const outingOptions = ref<{ label: string; value: string; type: 'OUTING' }[]>([]);

interface LineRow {
  category?: string;
  feeDate?: string;
  amount?: number;
  predocType?: string;
  predocProcessInstanceId?: string;
  remark?: string;
}

const formData = ref<{
  userNickname?: string;
  deptName?: string;
  periodLabel?: string;
  payeeAccountName?: string;
  payeeAccountNo?: string;
  lines: LineRow[];
}>({
  periodLabel: dayjs().format('YYYY-MM'),
  lines: [{}],
});

const categoryOptions = computed(() =>
  getDictOptions('finance_expense_category', 'string').map((d) => ({
    label: d.label,
    value: String(d.value),
  })),
);

function applyLoginUser() {
  formData.value.userNickname = userStore.userInfo?.nickname || '';
  formData.value.deptName = (userStore.userInfo as any)?.deptName || '';
}

async function loadPredocOptions() {
  try {
    const [trips, outings] = await Promise.all([
      getTripPage({ pageNo: 1, pageSize: 50 }),
      getOutingPage({ pageNo: 1, pageSize: 50 }),
    ]);
    tripOptions.value = (trips?.list || [])
      .filter((t) => Number(t.status) === 2 && t.processInstanceId)
      .map((t) => ({
        value: String(t.processInstanceId),
        type: 'TRIP' as const,
        label: `出差#${t.id} ${t.processInstanceId}`,
      }));
    outingOptions.value = (outings?.list || [])
      .filter((t) => Number(t.status) === 2 && t.processInstanceId)
      .map((t) => ({
        value: String(t.processInstanceId),
        type: 'OUTING' as const,
        label: `外出#${t.id} ${t.processInstanceId}`,
      }));
  } catch {
    tripOptions.value = [];
    outingOptions.value = [];
  }
}

function predocOptions(category?: string) {
  if (category === 'travel') return tripOptions.value;
  if (category === 'transport') return [...tripOptions.value, ...outingOptions.value];
  return [];
}

function onPredocChange(index: number, processInstanceId?: string) {
  const line = formData.value.lines[index];
  if (!line) return;
  line.predocProcessInstanceId = processInstanceId;
  const hit = [...tripOptions.value, ...outingOptions.value].find((o) => o.value === processInstanceId);
  line.predocType = hit?.type;
}

function addLine() {
  formData.value.lines.push({});
}

function removeLine(index: number) {
  if (formData.value.lines.length <= 1) {
    message.warning('至少一行明细');
    return;
  }
  formData.value.lines.splice(index, 1);
}

function lineTotal() {
  return formData.value.lines.reduce((s, l) => s + Number(l.amount || 0), 0);
}

function getPredictVariables(): Record<string, unknown> {
  return { applyAmount: lineTotal(), periodLabel: formData.value.periodLabel };
}

async function reset() {
  formData.value = {
    periodLabel: dayjs().format('YYYY-MM'),
    payeeAccountName: '',
    payeeAccountNo: '',
    lines: [{}],
  };
  applyLoginUser();
  emit('predictChange', getPredictVariables());
}

const rules: Record<string, Rule[]> = {
  periodLabel: [{ required: true, message: '请填写费用归属期间', trigger: 'blur' }],
  payeeAccountName: [{ required: true, message: '请填写收款户名', trigger: 'blur' }],
  payeeAccountNo: [{ required: true, message: '请填写收款账号', trigger: 'blur' }],
};

async function submit(): Promise<void> {
  await formRef.value?.validate();
  const lines = formData.value.lines
    .filter((l) => l.category && l.feeDate && Number(l.amount) > 0)
    .map((l) => ({
      lineKind: 'NORMAL' as const,
      category: String(l.category),
      feeDate: String(l.feeDate),
      amount: Number(l.amount),
      predocType:
        l.category === 'travel'
          ? 'TRIP'
          : l.category === 'transport'
            ? l.predocType || 'TRIP'
            : undefined,
      predocProcessInstanceId: l.predocProcessInstanceId,
      remark: l.remark,
    }));
  if (lines.length === 0) {
    message.warning('请至少填写一行完整明细');
    throw new Error('empty lines');
  }
  submitting.value = true;
  try {
    await createNoInvoiceExpense({
      periodLabel: String(formData.value.periodLabel),
      proxyTicket: false,
      payeeAccountName: String(formData.value.payeeAccountName),
      payeeAccountNo: String(formData.value.payeeAccountNo),
      lines,
    });
    message.success('提交成功');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

applyLoginUser();
onMounted(loadPredocOptions);
defineExpose({ reset, submit, getPredictVariables, submitting });
</script>

<template>
  <Form
    ref="formRef"
    :model="formData"
    :rules="rules"
    :label-col="{ span: 5 }"
    :wrapper-col="{ span: 18 }"
  >
    <Form.Item label="申请人">
      <Input :value="formData.userNickname" disabled />
    </Form.Item>
    <Form.Item label="部门">
      <Input :value="formData.deptName" disabled />
    </Form.Item>
    <Form.Item label="费用归属期间" name="periodLabel">
      <Input v-model:value="formData.periodLabel" placeholder="YYYY-MM" />
    </Form.Item>
    <Form.Item label="收款户名" name="payeeAccountName">
      <Input v-model:value="formData.payeeAccountName" placeholder="员工卡户名" />
    </Form.Item>
    <Form.Item label="收款账号" name="payeeAccountNo">
      <Input v-model:value="formData.payeeAccountNo" placeholder="员工卡账号" />
    </Form.Item>
    <Form.Item label="无票明细">
      <div
        v-for="(line, index) in formData.lines"
        :key="index"
        class="mb-2 flex flex-wrap items-center gap-2"
      >
        <Select v-model:value="line.category" class="w-28" :options="categoryOptions" placeholder="分类" />
        <DatePicker
          :value="line.feeDate ? dayjs(line.feeDate) : undefined"
          class="w-36"
          @change="(d) => (line.feeDate = d ? dayjs(d).format('YYYY-MM-DD') : undefined)"
        />
        <InputNumber v-model:value="line.amount" :min="0.01" :precision="2" placeholder="金额" />
        <Select
          v-if="line.category === 'travel' || line.category === 'transport'"
          :value="line.predocProcessInstanceId"
          class="w-56"
          :options="predocOptions(line.category)"
          placeholder="已通过出差/外出"
          allow-clear
          @change="(v) => onPredocChange(index, v as string)"
        />
        <Input v-model:value="line.remark" class="w-36" placeholder="说明" />
        <Button danger size="small" @click="removeLine(index)">删</Button>
      </div>
      <Button size="small" @click="addLine">加一行</Button>
      <div class="mt-1 text-xs text-gray-500">合计 {{ lineTotal().toFixed(2) }}</div>
    </Form.Item>
  </Form>
</template>
