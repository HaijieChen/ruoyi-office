<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';
import type { BpmOAPunchApi } from '#/api/bpm/oa/punch';

import { computed, ref } from 'vue';

import { BpmProcessInstanceStatus } from '@vben/constants';
import { useUserStore } from '@vben/stores';

import { DatePicker, Form, Input, TimePicker, message } from 'ant-design-vue';
import dayjs from 'dayjs';

import { createPunch, getPunch, getPunchRemaining } from '#/api/bpm/oa/punch';
import { FileUpload } from '#/components/upload';

import {
  combinePunchDateAndTime,
  remainingLabel,
  shouldFetchRemaining,
  toPunchDateStr,
} from '../punch-remaining';

defineOptions({ name: 'BpmOAPunchFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const remaining = ref<number | undefined>();
const remainingLoading = ref(false);
let lastFetchedDate: string | undefined;
let remainingSeq = 0;

const formData = ref({
  userNickname: '',
  deptName: '',
  punchDate: undefined as string | undefined,
  punchTimeDate: undefined as string | undefined,
  punchClock: undefined as string | undefined,
  reason: '',
  attachmentUrls: [] as string[],
});

const remainingText = computed(() =>
  remainingLabel(toPunchDateStr(formData.value.punchDate), remaining.value),
);

function getPredictVariables(): Record<string, unknown> {
  const vars: Record<string, unknown> = {};
  const punchDate = toPunchDateStr(formData.value.punchDate);
  if (punchDate) {
    vars.punchDate = punchDate;
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
    punchDate: undefined as string | undefined,
    punchTimeDate: undefined as string | undefined,
    punchClock: undefined as string | undefined,
    reason: '',
    attachmentUrls: [] as string[],
  };
}

function clearRemaining() {
  remaining.value = undefined;
  lastFetchedDate = undefined;
  remainingSeq += 1;
}

async function refreshRemaining() {
  const punchDate = toPunchDateStr(formData.value.punchDate);
  if (!shouldFetchRemaining(punchDate, lastFetchedDate)) {
    if (!punchDate) {
      clearRemaining();
    }
    return;
  }
  const seq = ++remainingSeq;
  remainingLoading.value = true;
  remaining.value = undefined;
  try {
    const count = await getPunchRemaining(punchDate as string);
    if (seq !== remainingSeq) {
      return;
    }
    remaining.value = typeof count === 'number' ? count : undefined;
    lastFetchedDate = punchDate;
  } catch {
    if (seq === remainingSeq) {
      remaining.value = undefined;
      lastFetchedDate = punchDate;
    }
  } finally {
    if (seq === remainingSeq) {
      remainingLoading.value = false;
    }
  }
}

async function onPunchDateChange() {
  notifyPredict();
  await refreshRemaining();
}

async function reset(opts?: { id?: number }) {
  formData.value = emptyForm();
  applyLoginUser();
  clearRemaining();
  if (opts?.id) {
    const data = await getPunch(Number(opts.id));
    if (!data) {
      message.error('重提补卡失败，原因：补卡数据不存在');
      notifyPredict();
      return;
    }
    if (data.status !== BpmProcessInstanceStatus.REJECT) {
      message.error('仅驳回的补卡单可以重提');
      notifyPredict();
      return;
    }
    const punchTime = data.punchTime ? dayjs(data.punchTime) : undefined;
    formData.value = {
      ...emptyForm(),
      punchDate: toPunchDateStr(data.punchDate),
      punchTimeDate: punchTime?.format('YYYY-MM-DD'),
      punchClock: punchTime?.format('HH:mm'),
      reason: data.reason,
      attachmentUrls: data.attachmentUrls || [],
    };
    applyLoginUser();
    await refreshRemaining();
  }
  notifyPredict();
}

const rules: Record<string, Rule[]> = {
  punchDate: [{ required: true, message: '请选择补卡日期', trigger: 'change' }],
  punchTimeDate: [
    { required: true, message: '请选择补卡时刻日期', trigger: 'change' },
  ],
  punchClock: [
    { required: true, message: '请选择补卡时刻', trigger: 'change' },
  ],
  reason: [{ required: true, message: '请填写补卡事由', trigger: 'blur' }],
};

function onAttach(v: string | string[]) {
  formData.value.attachmentUrls = Array.isArray(v) ? v : v ? [v] : [];
}

async function submit(ctx?: { startCompanyDeptId?: number }) {
  await formRef.value.validate();
  const punchDate = toPunchDateStr(formData.value.punchDate);
  const punchTime = combinePunchDateAndTime(
    formData.value.punchTimeDate,
    formData.value.punchClock,
  );
  if (!punchDate || punchTime == null) {
    message.warning('请完整填写补卡日期与补卡时刻');
    throw new Error('invalid punch fields');
  }
  const payload: BpmOAPunchApi.PunchCreate = {
    punchDate,
    punchTime,
    reason: formData.value.reason,
  };
  if (ctx?.startCompanyDeptId != null) {
    payload.startCompanyDeptId = ctx.startCompanyDeptId;
  }
  if (formData.value.attachmentUrls?.length) {
    payload.attachmentUrls = formData.value.attachmentUrls;
  }
  submitting.value = true;
  try {
    await createPunch(payload);
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
    <Form.Item label="补卡日期" name="punchDate">
      <DatePicker
        v-model:value="formData.punchDate"
        class="w-full"
        format="YYYY-MM-DD"
        value-format="YYYY-MM-DD"
        placeholder="请选择补卡日期"
        @change="onPunchDateChange"
      />
      <div
        v-if="remainingLoading"
        class="mt-1 text-sm text-[var(--ant-color-text-secondary)]"
      >
        查询本月剩余次数…
      </div>
      <div
        v-else-if="remainingText"
        class="mt-1 text-sm"
        :class="
          remaining === 0
            ? 'text-[var(--ant-color-warning)]'
            : 'text-[var(--ant-color-text-secondary)]'
        "
      >
        {{ remainingText }}
      </div>
    </Form.Item>
    <Form.Item label="补卡时刻" required>
      <div class="flex w-full gap-2">
        <Form.Item name="punchTimeDate" class="mb-0 flex-1" :colon="false">
          <DatePicker
            v-model:value="formData.punchTimeDate"
            class="w-full"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
          />
        </Form.Item>
        <Form.Item name="punchClock" class="mb-0 w-36" :colon="false">
          <TimePicker
            v-model:value="formData.punchClock"
            class="w-full"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="时刻"
          />
        </Form.Item>
      </div>
    </Form.Item>
    <Form.Item label="补卡事由" name="reason">
      <Input.TextArea
        v-model:value="formData.reason"
        :rows="3"
        placeholder="请输入补卡事由"
      />
    </Form.Item>
    <Form.Item label="附件">
      <FileUpload
        :value="formData.attachmentUrls || []"
        :max-number="10"
        :max-size="20"
        :multiple="true"
        help-text="说明附件（选填）"
        @update:value="onAttach"
      />
    </Form.Item>
  </Form>
</template>
