<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, ref } from 'vue';

import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { createExpenseReimbursement } from '#/api/finance/expense-reimbursement';

defineOptions({ name: 'FinanceExpenseReimbursementFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);

interface LineRow {
  lineKind: 'NORMAL' | 'PROXY';
  category?: string;
  feeDate?: string;
  amount?: number;
  remark?: string;
}

const formData = ref<{
  userNickname?: string;
  deptName?: string;
  periodLabel?: string;
  proxyTicket: boolean;
  payeeAccountName?: string;
  payeeAccountNo?: string;
  lines: LineRow[];
}>({
  proxyTicket: false,
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

function expectedKind(): 'NORMAL' | 'PROXY' {
  return formData.value.proxyTicket ? 'PROXY' : 'NORMAL';
}

function onProxyChange(checked: boolean) {
  formData.value.proxyTicket = checked;
  formData.value.lines = [{ lineKind: expectedKind() }];
  emit('predictChange', getPredictVariables());
}

function addLine() {
  formData.value.lines.push({ lineKind: expectedKind() });
}

function removeLine(index: number) {
  if (formData.value.lines.length <= 1) {
    message.warning('至少一行明细');
    return;
  }
  formData.value.lines.splice(index, 1);
}

function lineTotal(): number {
  return formData.value.lines.reduce((s, l) => s + Number(l.amount || 0), 0);
}

function getPredictVariables(): Record<string, unknown> {
  return {
    applyAmount: lineTotal(),
    periodLabel: formData.value.periodLabel,
    proxyTicket: formData.value.proxyTicket,
  };
}

async function reset() {
  formData.value = {
    proxyTicket: false,
    periodLabel: dayjs().format('YYYY-MM'),
    payeeAccountName: '',
    payeeAccountNo: '',
    lines: [{ lineKind: 'NORMAL' }],
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
  const kind = expectedKind();
  const lines = formData.value.lines
    .filter((l) => l.category && l.feeDate && Number(l.amount) > 0)
    .map((l) => ({
      lineKind: kind,
      category: String(l.category),
      feeDate: String(l.feeDate),
      amount: Number(l.amount),
      remark: l.remark,
    }));
  if (lines.length === 0) {
    message.warning('请至少填写一行完整明细');
    throw new Error('empty lines');
  }
  submitting.value = true;
  try {
    await createExpenseReimbursement({
      periodLabel: String(formData.value.periodLabel),
      proxyTicket: formData.value.proxyTicket,
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
    <Form.Item label="是否代票">
      <Switch :checked="formData.proxyTicket" @change="onProxyChange" />
    </Form.Item>
    <Form.Item label="费用归属期间" name="periodLabel">
      <Input
        v-model:value="formData.periodLabel"
        placeholder="YYYY-MM"
        @change="emit('predictChange', getPredictVariables())"
      />
    </Form.Item>
    <Form.Item label="收款户名" name="payeeAccountName">
      <Input v-model:value="formData.payeeAccountName" placeholder="员工卡户名" />
    </Form.Item>
    <Form.Item label="收款账号" name="payeeAccountNo">
      <Input v-model:value="formData.payeeAccountNo" placeholder="员工卡账号" />
    </Form.Item>
    <Form.Item :label="formData.proxyTicket ? '代票明细' : '普通明细'">
      <div
        v-for="(line, index) in formData.lines"
        :key="index"
        class="mb-2 flex flex-wrap items-center gap-2"
      >
        <Select
          v-if="!formData.proxyTicket"
          v-model:value="line.category"
          class="w-28"
          :options="categoryOptions"
          placeholder="分类"
        />
        <Input
          v-else
          v-model:value="line.category"
          class="w-28"
          placeholder="费用类型"
        />
        <DatePicker
          :value="line.feeDate ? dayjs(line.feeDate) : undefined"
          class="w-36"
          @change="(d) => (line.feeDate = d ? dayjs(d).format('YYYY-MM-DD') : undefined)"
        />
        <InputNumber v-model:value="line.amount" :min="0.01" :precision="2" placeholder="金额" />
        <Input v-model:value="line.remark" class="w-36" placeholder="说明" />
        <Button danger size="small" @click="removeLine(index)">删</Button>
      </div>
      <Button size="small" @click="addLine">加一行</Button>
      <div class="mt-1 text-xs text-gray-500">合计 {{ lineTotal().toFixed(2) }}</div>
    </Form.Item>
  </Form>
</template>
