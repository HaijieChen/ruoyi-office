<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { onMounted, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { DatePicker, Form, Input, Select, message } from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import { createTrip } from '#/api/bpm/oa/trip';
import { getSimpleUserList } from '#/api/system/user';

defineOptions({ name: 'BpmOATripFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const userOptions = ref<{ label: string; value: number }[]>([]);
const formData = ref<{
  userNickname?: string;
  deptName?: string;
  destination?: string;
  reason?: string;
  companionUserId?: number;
  range?: [Dayjs, Dayjs];
}>({});

function getPredictVariables(): Record<string, unknown> {
  const vars: Record<string, unknown> = {};
  if (formData.value.destination) vars.destination = formData.value.destination;
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
  destination: [{ required: true, message: '请填写出差地点', trigger: 'blur' }],
  reason: [{ required: true, message: '请填写出差原因', trigger: 'blur' }],
  companionUserId: [{ required: true, message: '请选择同行人员', trigger: 'change' }],
  range: [{ required: true, message: '请选择开始和结束日期', trigger: 'change' }],
};

async function submit(): Promise<void> {
  await formRef.value?.validate();
  const range = formData.value.range;
  if (!range?.[0] || !range?.[1] || range[1].isBefore(range[0], 'day')) {
    message.warning('结束日期不能早于开始日期');
    throw new Error('invalid range');
  }
  if (formData.value.companionUserId === userStore.userInfo?.id) {
    message.warning('同行人员须为组织内其他人员');
    throw new Error('companion');
  }
  submitting.value = true;
  try {
    await createTrip({
      destination: String(formData.value.destination).trim(),
      reason: String(formData.value.reason).trim(),
      companionUserId: Number(formData.value.companionUserId),
      startTime: range[0].startOf('day').valueOf(),
      endTime: range[1].endOf('day').valueOf(),
    });
    message.success('提交成功');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  applyLoginUser();
  const users = await getSimpleUserList();
  const selfId = userStore.userInfo?.id;
  userOptions.value = (users || [])
    .filter((u) => u.id !== selfId)
    .map((u) => ({ label: u.nickname || String(u.id), value: Number(u.id) }));
});

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
    <Form.Item label="出差地点" name="destination">
      <Input
        v-model:value="formData.destination"
        placeholder="请填写出差地点"
        @change="emit('predictChange', getPredictVariables())"
      />
    </Form.Item>
    <Form.Item label="出差日期" name="range">
      <DatePicker.RangePicker
        v-model:value="formData.range"
        class="w-full"
        format="YYYY-MM-DD"
        @change="emit('predictChange', getPredictVariables())"
      />
    </Form.Item>
    <Form.Item label="出差原因" name="reason">
      <Input.TextArea v-model:value="formData.reason" :rows="3" placeholder="请填写出差原因" />
    </Form.Item>
    <Form.Item label="同行人员" name="companionUserId">
      <Select
        v-model:value="formData.companionUserId"
        class="w-full"
        show-search
        option-filter-prop="label"
        :options="userOptions"
        placeholder="从组织架构选择 1 人"
      />
    </Form.Item>
  </Form>
</template>
