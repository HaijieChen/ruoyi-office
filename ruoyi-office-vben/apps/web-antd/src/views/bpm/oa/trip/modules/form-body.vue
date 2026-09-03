<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, onMounted, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import { DatePicker, Form, Input, InputNumber, Select, message } from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import { createTrip, getTrip } from '#/api/bpm/oa/trip';
import { getSimpleUserList } from '#/api/system/user';
import { FileUpload } from '#/components/upload';

import { calcTripHours } from '../data';

defineOptions({ name: 'BpmOATripFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const userStore = useUserStore();
const formRef = ref();
const submitting = ref(false);
const userOptions = ref<{ label: string; value: number }[]>([]);
const range = ref<[Dayjs, Dayjs] | undefined>();
const formData = ref<Record<string, any>>({});

const yesNoOptions = getDictOptions(DICT_TYPE.INFRA_BOOLEAN_STRING).map((d) => ({
  label: d.label,
  value: String(d.value),
}));
const bizTypeOptions = getDictOptions(DICT_TYPE.BPM_OA_TRIP_BIZ_TYPE, 'number');
const transportOptions = getDictOptions(DICT_TYPE.BPM_OA_TRIP_TRANSPORT, 'string');
const hotelOptions = getDictOptions(DICT_TYPE.BPM_OA_TRIP_HOTEL_BOOKING, 'string');
const cityOptions = getDictOptions(DICT_TYPE.OA_TRAVEL_CITY, 'string');

const bizType = computed(() => Number(formData.value.bizType || 0));
const isTalk = computed(() => bizType.value === 1);
const isEvent = computed(() => bizType.value === 2);
const isOther = computed(() => bizType.value === 3);
const hours = computed(() => {
  if (!range.value?.[0] || !range.value[1]) return undefined;
  return calcTripHours(range.value[0].valueOf(), range.value[1].valueOf());
});

function getPredictVariables(): Record<string, unknown> {
  const vars: Record<string, unknown> = {};
  if (formData.value.destination) vars.destination = formData.value.destination;
  if (hours.value != null) vars.hours = hours.value;
  return vars;
}

function notifyPredict() {
  emit('predictChange', getPredictVariables());
}

function onAttach(v: string | string[]) {
  formData.value.attachmentUrls = Array.isArray(v) ? v : v ? [v] : [];
}

function onBizTypeChange() {
  if (isOther.value) {
    formData.value.partyName = undefined;
    formData.value.address = undefined;
    formData.value.contactInfo = undefined;
    formData.value.needOutput = undefined;
    formData.value.hasCarriageFee = undefined;
  } else if (isTalk.value) {
    formData.value.needOutput = undefined;
    formData.value.hasCarriageFee = undefined;
  }
}

async function reset(opts?: {
  id?: number;
  mode?: string;
  copyFromBusinessKey?: string;
}) {
  formData.value = { attachmentUrls: [] };
  range.value = undefined;
  const copyId = Number(opts?.copyFromBusinessKey);
  const loadId =
    opts?.id ??
    (Number.isFinite(copyId) && copyId > 0 ? copyId : undefined);
  if (loadId) {
    const trip = await getTrip(Number(loadId));
    formData.value = {
      bizType: trip.bizType,
      originCity: trip.originCity,
      destination: trip.destination,
      reason: trip.reason,
      transport: trip.transport,
      hotelBooking: trip.hotelBooking,
      partyName: trip.bizType ? trip.partyName : undefined,
      address: trip.bizType ? trip.address : undefined,
      contactInfo: trip.bizType ? trip.contactInfo : undefined,
      needOutput: trip.needOutput,
      hasCarriageFee: trip.hasCarriageFee,
      remark: trip.remark,
      attachmentUrls: trip.attachmentUrls || [],
      companionUserIds: trip.companionUserIds || (trip.companionUserId ? [trip.companionUserId] : []),
    };
    if (trip.startTime && trip.endTime) {
      range.value = [dayjs(trip.startTime), dayjs(trip.endTime)];
    }
  }
  notifyPredict();
}

const rules: Record<string, Rule[]> = {
  bizType: [{ required: true, message: '请选择出差类型', trigger: 'change' }],
  reason: [{ required: true, message: '请填写出差事由', trigger: 'blur' }],
  originCity: [{ required: true, message: '请选择出发城市', trigger: 'change' }],
  destination: [{ required: true, message: '请选择目的地城市', trigger: 'change' }],
  transport: [{ required: true, message: '请选择交通工具', trigger: 'change' }],
  companionUserIds: [{ required: true, type: 'array', min: 1, message: '请选择同行人员', trigger: 'change' }],
};

async function submit(ctx?: { startCompanyDeptId?: number; startDeptId?: number }): Promise<void> {
  await formRef.value?.validate();
  if (!range.value?.[0] || !range.value[1] || hours.value == null) {
    message.warning('结束时间必须晚于开始时间，且时长须大于 0');
    throw new Error('invalid range');
  }
  const companionUserIds = (formData.value.companionUserIds || []).map(Number).filter(Boolean);
  if (companionUserIds.length === 0 || companionUserIds.includes(Number(userStore.userInfo?.id))) {
    message.warning('同行人员须为组织内其他人员');
    throw new Error('companion');
  }
  const attachments = (formData.value.attachmentUrls || []).filter(Boolean);
  if (!attachments.length) {
    message.warning('请上传附件');
    throw new Error('attach');
  }
  if ((isTalk.value || isEvent.value) && (!formData.value.partyName || !formData.value.address
    || !formData.value.contactInfo || !formData.value.hotelBooking)) {
    message.warning('请填写类型明细必填项');
    throw new Error('detail');
  }
  if (isEvent.value && (!formData.value.needOutput || !formData.value.hasCarriageFee)) {
    message.warning('请选择是否需要内容产出和是否有车马费');
    throw new Error('event');
  }
  submitting.value = true;
  try {
    const payload: any = {
      bizType: Number(formData.value.bizType),
      originCity: formData.value.originCity,
      destination: formData.value.destination,
      reason: String(formData.value.reason).trim(),
      transport: formData.value.transport,
      companionUserIds,
      attachmentUrls: attachments,
      startTime: range.value[0].valueOf(),
      endTime: range.value[1].valueOf(),
      startCompanyDeptId: ctx?.startCompanyDeptId,
      startDeptId: ctx?.startDeptId,
      remark: formData.value.remark,
    };
    if (!isOther.value) {
      payload.hotelBooking = formData.value.hotelBooking;
      payload.partyName = formData.value.partyName;
      payload.address = formData.value.address;
      payload.contactInfo = formData.value.contactInfo;
    } else if (formData.value.hotelBooking) {
      payload.hotelBooking = formData.value.hotelBooking;
    }
    if (isEvent.value) {
      payload.needOutput = formData.value.needOutput;
      payload.hasCarriageFee = formData.value.hasCarriageFee;
    }
    await createTrip(payload);
    message.success('提交成功');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
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
    <Form.Item label="出差类型" name="bizType">
      <Select
        v-model:value="formData.bizType"
        class="w-full"
        :options="bizTypeOptions"
        placeholder="请选择"
        @change="onBizTypeChange(); notifyPredict()"
      />
    </Form.Item>
    <Form.Item label="出差事由" name="reason">
      <Input.TextArea v-model:value="formData.reason" :rows="3" placeholder="请填写" />
    </Form.Item>
    <Form.Item label="起止时间" required>
      <DatePicker.RangePicker
        v-model:value="range"
        class="w-full"
        show-time
        format="YYYY-MM-DD HH:mm"
        @change="notifyPredict"
      />
    </Form.Item>
    <Form.Item label="出差时长">
      <InputNumber :value="hours" class="w-full" disabled :precision="1" addon-after="小时" />
    </Form.Item>
    <Form.Item label="交通工具" name="transport">
      <Select v-model:value="formData.transport" class="w-full" :options="transportOptions" placeholder="请选择" />
    </Form.Item>
    <Form.Item label="出发城市" name="originCity">
      <Select
        v-model:value="formData.originCity"
        class="w-full"
        show-search
        option-filter-prop="label"
        :options="cityOptions"
        placeholder="请选择"
      />
    </Form.Item>
    <Form.Item label="目的地城市" name="destination">
      <Select
        v-model:value="formData.destination"
        class="w-full"
        show-search
        option-filter-prop="label"
        :options="cityOptions"
        placeholder="请选择"
        @change="notifyPredict"
      />
    </Form.Item>
    <template v-if="isTalk || isEvent">
      <Form.Item :label="isTalk ? '业务公司全称' : '活动邀请方'" required>
        <Input v-model:value="formData.partyName" placeholder="请填写" />
      </Form.Item>
      <Form.Item :label="isTalk ? '业务公司具体地址' : '活动举办详细地址'" required>
        <Input v-model:value="formData.address" placeholder="请填写" />
      </Form.Item>
      <Form.Item label="对接人姓名、职务、联系方式" required>
        <Input.TextArea v-model:value="formData.contactInfo" :rows="2" placeholder="请填写" />
      </Form.Item>
    </template>
    <Form.Item v-if="bizType" :label="'机酒预定情况'" :required="!isOther">
      <Select v-model:value="formData.hotelBooking" class="w-full" allow-clear :options="hotelOptions" placeholder="请选择" />
    </Form.Item>
    <template v-if="isEvent">
      <Form.Item label="是否需要内容产出" required>
        <Select v-model:value="formData.needOutput" class="w-full" :options="yesNoOptions" placeholder="请选择" />
      </Form.Item>
      <Form.Item label="是否有车马费" required>
        <Select v-model:value="formData.hasCarriageFee" class="w-full" :options="yesNoOptions" placeholder="请选择" />
      </Form.Item>
    </template>
    <Form.Item label="备注">
      <Input v-model:value="formData.remark" placeholder="请填写" />
    </Form.Item>
    <Form.Item label="同行人员" name="companionUserIds">
      <Select
        v-model:value="formData.companionUserIds"
        class="w-full"
        mode="multiple"
        show-search
        option-filter-prop="label"
        :options="userOptions"
        placeholder="可选择多人，计入住宿房间数"
      />
    </Form.Item>
    <Form.Item label="附件" required>
      <FileUpload
        :value="formData.attachmentUrls || []"
        :max-number="10"
        :max-size="20"
        :multiple="true"
        help-text="出差对接、机酒预定信息（无票时可先传对接材料）"
        @update:value="onAttach"
      />
    </Form.Item>
  </Form>
</template>
