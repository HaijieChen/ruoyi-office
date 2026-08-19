<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { ref } from 'vue';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';
import { DatePicker, Form, Input, InputNumber, Select, message } from 'ant-design-vue';
import { createOuting } from '#/api/bpm/oa/outing';
import { FileUpload } from '#/components/upload';
import { calcOutingHours } from '../data';
defineOptions({ name: 'BpmOAOutingFormBody' });
const emit = defineEmits(['predictChange', 'success']);
const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const formData = ref({ userNickname: '', deptName: '', reason: '', location: '', hours: undefined, needOutput: undefined, attachmentUrls: [] });
const range = ref();
const yesNoOptions = getDictOptions(DICT_TYPE.INFRA_BOOLEAN_STRING).map(function (d) { return { label: d.label, value: String(d.value) }; });
function syncHours() {
  if (!range.value || !range.value[0] || !range.value[1]) { formData.value.hours = undefined; return; }
  formData.value.hours = calcOutingHours(range.value[0].valueOf(), range.value[1].valueOf());
}
function getPredictVariables() {
  syncHours();
  const vars = {};
  if (formData.value.hours != null) vars.hours = formData.value.hours;
  if (formData.value.needOutput) vars.need_output = formData.value.needOutput;
  return vars;
}
function notifyPredict() { emit('predictChange', getPredictVariables()); }
function applyLoginUser() {
  formData.value.userNickname = (userStore.userInfo && userStore.userInfo.nickname) || '';
  formData.value.deptName = (userStore.userInfo && userStore.userInfo.deptName) || '';
}
async function reset() {
  formData.value = { userNickname: '', deptName: '', reason: '', location: '', hours: undefined, needOutput: undefined, attachmentUrls: [] };
  range.value = undefined;
  applyLoginUser();
  notifyPredict();
}
const rules = {
  reason: [{ required: true, message: '请填写外出事由', trigger: 'blur' }],
  location: [{ required: true, message: '请填写外出地点', trigger: 'blur' }],
};
function onAttach(v) {
  formData.value.attachmentUrls = Array.isArray(v) ? v : (v ? [v] : []);
}
async function submit() {
  await formRef.value.validate();
  syncHours();
  if (!range.value || !range.value[0] || !range.value[1] || formData.value.hours == null) {
    message.warning('结束时间必须晚于开始时间，且时长须大于 0');
    throw new Error('invalid range');
  }
  const payload = { reason: formData.value.reason, location: formData.value.location, startTime: range.value[0].valueOf(), endTime: range.value[1].valueOf() };
  if (formData.value.needOutput) payload.needOutput = formData.value.needOutput;
  if (formData.value.attachmentUrls && formData.value.attachmentUrls.length) payload.attachmentUrls = formData.value.attachmentUrls;
  submitting.value = true;
  try {
    await createOuting(payload);
    message.success('提交成功');
    emit('success');
  } finally { submitting.value = false; }
}
defineExpose({ reset: reset, submit: submit, getPredictVariables: getPredictVariables, submitting: submitting });
</script>

<template>
  <Form ref="formRef" :model="formData" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
    <Form.Item label="申请人"><Input :value="formData.userNickname" disabled /></Form.Item>
    <Form.Item label="部门"><Input :value="formData.deptName" disabled /></Form.Item>
    <Form.Item label="外出事由" name="reason"><Input.TextArea v-model:value="formData.reason" :rows="3" placeholder="请输入外出事由" /></Form.Item>
    <Form.Item label="外出地点" name="location"><Input v-model:value="formData.location" placeholder="请输入外出地点" /></Form.Item>
    <Form.Item label="起止时间" required>
      <DatePicker.RangePicker v-model:value="range" class="w-full" show-time format="YYYY-MM-DD HH:mm" @change="notifyPredict" />
    </Form.Item>
    <Form.Item label="外出时长"><InputNumber :value="formData.hours" class="w-full" disabled :precision="1" /></Form.Item>
    <Form.Item label="是否需要内容产出">
      <Select v-model:value="formData.needOutput" class="w-full" allow-clear :options="yesNoOptions" placeholder="请选择" @change="notifyPredict" />
    </Form.Item>
    <Form.Item label="附件">
      <FileUpload :value="formData.attachmentUrls || []" :max-number="10" :max-size="20" :multiple="true" help-text="外出对接相关内容" @update:value="onAttach" />
    </Form.Item>
  </Form>
</template>
