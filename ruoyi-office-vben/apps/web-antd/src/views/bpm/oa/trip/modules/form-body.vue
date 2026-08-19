<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import { DatePicker, Form, Input, InputNumber, Select, message } from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import { createTrip } from '#/api/bpm/oa/trip';
import { calcTripHours } from '../data';

defineOptions({ name: 'BpmOATripFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const formData = ref<{
  userNickname?: string;
  deptName?: string;
  type?: number;
  range?: [Dayjs, Dayjs];
  hours?: number;
}>({});

const typeOptions = computed(() =>
  getDictOptions(DICT_TYPE.BPM_OA_TRIP_TYPE, 'number').map((d) => ({
    label: d.label,
    value: d.value as number,
  })),
);

function syncHours() {
  const range = formData.value.range;
  if (!range?.[0] || !range?.[1]) {
    formData.value.hours = undefined;
    return;
  }
  formData.value.hours = calcTripHours(range[0].valueOf(), range[1].valueOf());
}

function getPredictVariables(): Record<string, unknown> {
  syncHours();
  const vars: Record<string, unknown> = {};
  if (formData.value.hours != null) vars.hours = formData.value.hours;
  if (formData.value.type != null) vars.type = formData.value.type;
  return vars;
}

function applyLoginUser() {
  formData.value.userNickname = userStore.userInfo?.nickname || '';
  formData.value.deptName = (userStore.userInfo as any)?.deptName || '';
}

async function reset(_opts?: { id?: number; mode?: string }) {
  formData.value = {};
  applyLoginUser();
  emit('predictChange', getPredictVariables());
}

const rules: Record<string, Rule[]> = {
  type: [{ required: true, message: '请选择出差类型', trigger: 'change' }],
  range: [{ required: true, message: '请选择开始和结束时间', trigger: 'change' }],
};

async function submit(): Promise<void> {
  await formRef.value?.validate();
  syncHours();
  const range = formData.value.range;
  if (!range?.[0] || !range?.[1] || formData.value.hours == null) {
    message.warning('结束时间必须晚于开始时间，且时长须大于 0');
    throw new Error('invalid range');
  }
  submitting.value = true;
  try {
    await createTrip({
      type: Number(formData.value.type),
      startTime: range[0].valueOf(),
      endTime: range[1].valueOf(),
    });
    message.success('提交成功');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

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
    <Form.Item label="出差类型" name="type">
      <Select
        v-model:value="formData.type"
        class="w-full"
        :options="typeOptions"
        placeholder="请选择"
        @change="emit('predictChange', getPredictVariables())"
      />
    </Form.Item>
    <Form.Item label="起止时间" name="range">
      <DatePicker.RangePicker
        v-model:value="formData.range"
        class="w-full"
        show-time
        format="YYYY-MM-DD HH:mm"
        @change="emit('predictChange', getPredictVariables())"
      />
    </Form.Item>
    <Form.Item label="出差时长">
      <InputNumber :value="formData.hours" class="w-full" disabled :precision="1" />
    </Form.Item>
  </Form>
</template>
