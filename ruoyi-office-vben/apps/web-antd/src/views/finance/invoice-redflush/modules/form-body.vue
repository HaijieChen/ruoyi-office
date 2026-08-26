<script lang="ts" setup>
import type { FinanceInvoiceApplicationApi } from '#/api/finance/invoice-application';

import { ref } from 'vue';

import { Button, Form, Input, Modal, Select, message } from 'ant-design-vue';

import { getInvoiceApplication } from '#/api/finance/invoice-application';
import {
  createAndStartInvoiceRedflush,
  listRedflushPredecessors,
} from '#/api/finance/invoice-redflush';

import InvoiceInfo from '../../invoice-application/info/index.vue';

defineOptions({ name: 'FinanceInvoiceRedflushFormBody' });

const emit = defineEmits<{ success: [] }>();

const submitting = ref(false);
const predecessorId = ref<number>();
const reason = ref('');
const specialNote = ref('');
const options = ref<{ label: string; value: number; amount: number }[]>([]);
const snapshot = ref<FinanceInvoiceApplicationApi.Application>();
const infoOpen = ref(false);

async function loadOptions() {
  const list = (await listRedflushPredecessors()) || [];
  options.value = list.map((a) => ({
    value: a.id,
    amount: Number(a.totalAmount || 0),
    label: `${a.applicationNo} · ${a.buyerName || ''} · ${a.totalAmount ?? ''}`,
  }));
}

async function onPick(id: number) {
  predecessorId.value = id;
  snapshot.value = await getInvoiceApplication(id);
}

function openOriginal() {
  if (!predecessorId.value) return;
  infoOpen.value = true;
}

async function reset() {
  predecessorId.value = undefined;
  reason.value = '';
  specialNote.value = '';
  snapshot.value = undefined;
  await loadOptions();
}

function getPredictVariables() {
  return { totalAmount: snapshot.value?.totalAmount };
}

async function submit(ctx?: {
  startUserSelectAssignees?: Record<string, number[]>;
  startCompanyDeptId?: number; startDeptId?: number;
}) {
  if (!predecessorId.value) {
    message.warning('请选择前置开票申请');
    throw new Error('请选择前置开票申请');
  }
  if (!reason.value.trim()) {
    message.warning('请填写红冲原因');
    throw new Error('请填写红冲原因');
  }
  submitting.value = true;
  try {
    await createAndStartInvoiceRedflush({
      predecessorApplicationId: predecessorId.value,
      reason: reason.value.trim(),
      specialNote: specialNote.value || undefined,
      totalAmount: snapshot.value?.totalAmount,
      startUserSelectAssignees: ctx?.startUserSelectAssignees,
      startCompanyDeptId: ctx?.startCompanyDeptId,
      startDeptId: ctx?.startDeptId,
    });
    message.success('已提交红冲申请');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

async function onSubmit() {
  await submit();
}

defineExpose({ reset, submit, getPredictVariables, submitting });

loadOptions();
</script>

<template>
  <Form layout="vertical">
    <Form.Item label="前置开票申请" required>
      <Select
        :value="predecessorId"
        :options="options"
        show-search
        option-filter-prop="label"
        placeholder="暂无符合条件的已开票申请"
        @change="(v: number) => onPick(v)"
      />
    </Form.Item>
    <Form.Item v-if="snapshot" label="原单金额">
      <Input :value="String(snapshot.totalAmount ?? '')" disabled />
      <Button class="ml-2" type="link" @click="openOriginal">查看原开票申请</Button>
    </Form.Item>
    <Form.Item v-if="snapshot" label="购方">
      <Input :value="snapshot.buyerName" disabled />
    </Form.Item>
    <Form.Item label="红冲原因" required>
      <Input.TextArea v-model:value="reason" :rows="3" />
    </Form.Item>
    <Form.Item label="特殊情况说明">
      <Input.TextArea v-model:value="specialNote" :rows="2" />
    </Form.Item>
    <Button type="primary" :loading="submitting" @click="onSubmit">提交</Button>
  </Form>
  <Modal v-model:open="infoOpen" title="原开票申请" width="800px" :footer="null">
    <InvoiceInfo v-if="predecessorId" :id="String(predecessorId)" />
  </Modal>
</template>
