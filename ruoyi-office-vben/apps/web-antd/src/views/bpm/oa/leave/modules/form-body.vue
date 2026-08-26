<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { onMounted, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import { DatePicker, Form, Input, Select, message } from 'ant-design-vue';
import type { Dayjs } from 'dayjs';

import { createLeave } from '#/api/bpm/oa/leave';

defineOptions({ name: 'BpmOALeaveFormBody' });

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
  reason?: string;
  range?: [Dayjs, Dayjs];
}>({});

function applyLoginUser() {
  formData.value.userNickname = userStore.userInfo?.nickname || '';
  formData.value.deptName = (userStore.userInfo as { deptName?: string })?.deptName || '';
}

async function reset() {
  formData.value = {};
  applyLoginUser();
  emit('predictChange', {});
}

const rules: Record<string, Rule[]> = {
  type: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  reason: [{ required: true, message: '请填写请假原因', trigger: 'blur' }],
  range: [{ required: true, message: '请选择开始和结束时间', trigger: 'change' }],
};

async function submit(ctx?: { startCompanyDeptId?: number }): Promise<void> {
  await formRef.value?.validate();
  const range = formData.value.range;
  if (!range?.[0] || !range?.[1] || range[1].isBefore(range[0])) {
    message.warning('结束时间不能早于开始时间');
    throw new Error('invalid range');
  }
  submitting.value = true;
  try {
    await createLeave({
      type: Number(formData.value.type),
      reason: String(formData.value.reason).trim(),
      startTime: range[0].valueOf(),
      endTime: range[1].valueOf(),
      startCompanyDeptId: ctx?.startCompanyDeptId,
    } as any);
    message.success('提交成功');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

onMounted(applyLoginUser);
defineExpose({ reset, submit, getPredictVariables: () => ({}), submitting });
</script>

<template>
  <Form ref="formRef" :model="formData" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
    <Form.Item label="申请人">
      <Input :value="formData.userNickname" disabled />
    </Form.Item>
    <Form.Item label="部门">
      <Input :value="formData.deptName" disabled />
    </Form.Item>
    <Form.Item label="请假类型" name="type">
      <Select
        v-model:value="formData.type"
        class="w-full"
        :options="getDictOptions(DICT_TYPE.BPM_OA_LEAVE_TYPE, 'number')"
        placeholder="请选择请假类型"
      />
    </Form.Item>
    <Form.Item label="请假时间" name="range">
      <DatePicker.RangePicker
        v-model:value="formData.range"
        class="w-full"
        show-time
        format="YYYY-MM-DD HH:mm"
      />
    </Form.Item>
    <Form.Item label="原因" name="reason">
      <Input.TextArea v-model:value="formData.reason" :rows="3" placeholder="请填写请假原因" />
    </Form.Item>
  </Form>
</template>
