<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';
import type { BpmOAOvertimeApi } from '#/api/bpm/oa/overtime';

import { computed, ref } from 'vue';

import { BpmProcessInstanceStatus } from '@vben/constants';
import { useUserStore } from '@vben/stores';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  TimePicker,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { createOvertime, getOvertime } from '#/api/bpm/oa/overtime';
import { FileUpload } from '#/components/upload';

import {
  getOvertimeHolidayOptions,
} from '../data';
import {
  calcOvertimeHours,
  combineDateAndTime,
  getOvertimeRangeError,
  previewOvertimeHours,
} from '../overtime-hours';

defineOptions({ name: 'BpmOAOvertimeFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const holidayOptions = getOvertimeHolidayOptions();

const formData = ref({
  userNickname: '',
  deptName: '',
  reason: '',
  startDate: undefined as string | undefined,
  startClock: undefined as string | undefined,
  endDate: undefined as string | undefined,
  endClock: undefined as string | undefined,
  holiday: undefined as string | undefined,
  attachmentUrls: [] as string[],
});

const startMs = computed(() =>
  combineDateAndTime(formData.value.startDate, formData.value.startClock),
);
const endMs = computed(() =>
  combineDateAndTime(formData.value.endDate, formData.value.endClock),
);
const displayHours = computed(() =>
  previewOvertimeHours(startMs.value, endMs.value),
);

function getPredictVariables(): Record<string, unknown> {
  const vars: Record<string, unknown> = {};
  const hours = calcOvertimeHours(startMs.value, endMs.value);
  if (hours != null) {
    vars.hours = hours;
  }
  if (formData.value.holiday === 'true') {
    vars.holiday = true;
  } else if (formData.value.holiday === 'false') {
    vars.holiday = false;
  }
  return vars;
}

function notifyPredict() {
  emit('predictChange', getPredictVariables());
}

function applyLoginUser() {
  formData.value.userNickname = userStore.userInfo?.nickname || '';
  formData.value.deptName = userStore.userInfo?.deptName || '';
}

function emptyForm() {
  return {
    userNickname: '',
    deptName: '',
    reason: '',
    startDate: undefined as string | undefined,
    startClock: undefined as string | undefined,
    endDate: undefined as string | undefined,
    endClock: undefined as string | undefined,
    holiday: undefined as string | undefined,
    attachmentUrls: [] as string[],
  };
}

async function reset(opts?: { id?: number }) {
  formData.value = emptyForm();
  applyLoginUser();
  if (opts?.id) {
    const data = await getOvertime(Number(opts.id));
    if (!data) {
      message.error('重提加班失败，原因：加班数据不存在');
      notifyPredict();
      return;
    }
    if (data.status !== BpmProcessInstanceStatus.REJECT) {
      message.error('仅驳回的加班单可以重提');
      notifyPredict();
      return;
    }
    const start = data.startTime ? dayjs(data.startTime) : undefined;
    const end = data.endTime ? dayjs(data.endTime) : undefined;
    formData.value = {
      ...emptyForm(),
      reason: data.reason,
      startDate: start?.format('YYYY-MM-DD'),
      startClock: start?.format('HH:mm'),
      endDate: end?.format('YYYY-MM-DD'),
      endClock: end?.format('HH:mm'),
      holiday: data.holiday,
      attachmentUrls: data.attachmentUrls || [],
    };
    applyLoginUser();
  }
  notifyPredict();
}

const rules: Record<string, Rule[]> = {
  reason: [{ required: true, message: '请填写加班事由', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  startClock: [{ required: true, message: '请选择开始时刻', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  endClock: [{ required: true, message: '请选择结束时刻', trigger: 'change' }],
  holiday: [{ required: true, message: '请选择是否法定节假日', trigger: 'change' }],
};

function onAttach(v: string | string[]) {
  formData.value.attachmentUrls = Array.isArray(v) ? v : v ? [v] : [];
}

async function submit(ctx?: { startCompanyDeptId?: number }) {
  await formRef.value.validate();
  const rangeError = getOvertimeRangeError(startMs.value, endMs.value);
  if (rangeError || calcOvertimeHours(startMs.value, endMs.value) == null) {
    message.warning(rangeError || '结束时间必须晚于开始时间，且时长至少 2 小时');
    throw new Error('invalid range');
  }
  const payload: BpmOAOvertimeApi.OvertimeCreate = {
    reason: formData.value.reason,
    startTime: startMs.value as number,
    endTime: endMs.value as number,
    holiday: formData.value.holiday as string,
  };
  if (ctx?.startCompanyDeptId != null) {
    payload.startCompanyDeptId = ctx.startCompanyDeptId;
  }
  if (formData.value.attachmentUrls?.length) {
    payload.attachmentUrls = formData.value.attachmentUrls;
  }
  submitting.value = true;
  try {
    await createOvertime(payload);
    message.success('提交成功');
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
    <Form.Item label="加班事由" name="reason">
      <Input.TextArea
        v-model:value="formData.reason"
        :rows="3"
        placeholder="请输入加班事由"
      />
    </Form.Item>
    <Form.Item label="开始时间" required>
      <div class="flex w-full gap-2">
        <Form.Item name="startDate" class="mb-0 flex-1" :colon="false">
          <DatePicker
            v-model:value="formData.startDate"
            class="w-full"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
            @change="notifyPredict"
          />
        </Form.Item>
        <Form.Item name="startClock" class="mb-0 w-36" :colon="false">
          <TimePicker
            v-model:value="formData.startClock"
            class="w-full"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="时刻"
            @change="notifyPredict"
          />
        </Form.Item>
      </div>
    </Form.Item>
    <Form.Item label="结束时间" required>
      <div class="flex w-full gap-2">
        <Form.Item name="endDate" class="mb-0 flex-1" :colon="false">
          <DatePicker
            v-model:value="formData.endDate"
            class="w-full"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
            @change="notifyPredict"
          />
        </Form.Item>
        <Form.Item name="endClock" class="mb-0 w-36" :colon="false">
          <TimePicker
            v-model:value="formData.endClock"
            class="w-full"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="时刻"
            @change="notifyPredict"
          />
        </Form.Item>
      </div>
    </Form.Item>
    <Form.Item label="加班时长">
      <InputNumber
        :value="displayHours"
        class="w-full"
        disabled
        :precision="1"
        placeholder="选择起止时间后自动计算"
      />
    </Form.Item>
    <Form.Item label="是否法定节假日" name="holiday">
      <Select
        v-model:value="formData.holiday"
        class="w-full"
        :options="holidayOptions"
        placeholder="请选择"
        @change="notifyPredict"
      />
    </Form.Item>
    <Form.Item label="附件">
      <FileUpload
        :value="formData.attachmentUrls || []"
        :max-number="10"
        :max-size="20"
        :multiple="true"
        help-text="加班说明附件（选填）"
        @update:value="onAttach"
      />
    </Form.Item>
  </Form>
</template>
