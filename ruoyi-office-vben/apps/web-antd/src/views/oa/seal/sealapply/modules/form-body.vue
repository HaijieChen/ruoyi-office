<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import { computed, onMounted, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import { getSealApplyBill, submitSealApplyBill } from '#/api/oa/seal/sealapply';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'OaSealApplyFormBody' });

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
  companyName?: string;
  companyId?: number;
  deptId?: number;
  sealName?: string;
  useType?: number;
  useMode?: number;
  documentTitle?: string;
  documentType?: string;
  documentCount?: number;
  contractAmount?: number;
  contractParty?: string;
  expectedUseTime?: Dayjs;
  expectedReturnTime?: Dayjs;
  isUrgent?: number;
  cause?: string;
  remark?: string;
  attachmentUrls?: string[];
}>({});

const isContractUse = computed(() => formData.value.useType === 1);

function applyLoginUser() {
  const info = userStore.userInfo as {
    nickname?: string;
    deptName?: string;
    companyName?: string;
    companyId?: number;
    deptId?: number;
  };
  formData.value.userNickname = info?.nickname || '';
  formData.value.deptName = info?.deptName || '';
  formData.value.companyName = info?.companyName || '';
  formData.value.companyId = info?.companyId;
  formData.value.deptId = info?.deptId;
}

function toDayjs(value: unknown): Dayjs | undefined {
  if (value == null || value === '') return undefined;
  const d = dayjs(value as string | Date);
  return d.isValid() ? d : undefined;
}

async function reset(opts?: {
  mode?: string;
  copyFrom?: Record<string, any>;
  copyFromBusinessKey?: string;
}) {
  formData.value = {
    useType: 1,
    useMode: 1,
    documentCount: 1,
    isUrgent: 0,
    attachmentUrls: [],
  };
  applyLoginUser();
  const billId = Number(opts?.copyFromBusinessKey);
  if (Number.isFinite(billId) && billId > 0) {
    try {
      const bill = await getSealApplyBill(billId);
      formData.value = {
        ...formData.value,
        sealId: bill.sealId,
        sealNo: bill.sealNo,
        sealName: bill.sealName,
        sealType: bill.sealType,
        keeperId: bill.keeperId,
        keeperName: bill.keeperName,
        keeperDeptId: bill.keeperDeptId,
        keeperDeptName: bill.keeperDeptName,
        useType: bill.useType ?? 1,
        useMode: bill.useMode ?? 1,
        documentTitle: bill.documentTitle,
        documentType: bill.documentType,
        documentCount: bill.documentCount ?? 1,
        contractAmount: bill.contractAmount,
        contractParty: bill.contractParty,
        expectedUseTime: toDayjs(bill.expectedUseTime),
        expectedReturnTime: toDayjs(bill.expectedReturnTime),
        isUrgent: bill.isUrgent ?? 0,
        cause: bill.cause,
        remark: bill.remark,
        attachmentUrls: (bill.attachments || [])
          .map((a) => a.fileUrl || a.filePath)
          .filter(Boolean) as string[],
      };
      applyLoginUser();
    } catch {
      /* 再提带数失败仍可空白发起 */
    }
  }
  emit('predictChange', {});
}

const rules: Record<string, Rule[]> = {
  sealName: [{ required: true, message: '请输入印章', trigger: 'blur' }],
  useType: [{ required: true, message: '请选择用章类型', trigger: 'change' }],
  useMode: [{ required: true, message: '请选择用章方式', trigger: 'change' }],
  expectedUseTime: [
    { required: true, message: '请选择使用日期', trigger: 'change' },
  ],
  cause: [{ required: true, message: '请填写用章事由', trigger: 'blur' }],
};

async function submit(_ctx?: { startCompanyDeptId?: number }): Promise<void> {
  await formRef.value?.validate();
  const sealName = String(formData.value.sealName || '').trim();
  if (!sealName) {
    message.warning('请输入印章');
    throw new Error('seal required');
  }
  if (
    formData.value.expectedReturnTime &&
    formData.value.expectedUseTime &&
    !formData.value.expectedReturnTime.isAfter(formData.value.expectedUseTime)
  ) {
    message.warning('预计归还日期须晚于使用日期');
    throw new Error('return time');
  }
  submitting.value = true;
  try {
    const urls = formData.value.attachmentUrls || [];
    await submitSealApplyBill({
      billCode: '',
      sealName,
      cause: String(formData.value.cause).trim(),
      useType: Number(formData.value.useType),
      useMode: Number(formData.value.useMode),
      documentTitle: formData.value.documentTitle,
      documentType: formData.value.documentType,
      documentCount: formData.value.documentCount || 1,
      contractAmount: isContractUse.value
        ? formData.value.contractAmount
        : undefined,
      contractParty: isContractUse.value
        ? formData.value.contractParty
        : undefined,
      expectedUseTime: formData.value.expectedUseTime?.toDate(),
      expectedReturnTime: formData.value.expectedReturnTime?.toDate(),
      isUrgent: formData.value.isUrgent ?? 0,
      creatorName: formData.value.userNickname,
      companyId: formData.value.companyId || 0,
      companyName: formData.value.companyName || '',
      deptId: formData.value.deptId || 0,
      deptName: formData.value.deptName || '',
      remark: formData.value.remark,
      attachments: urls.map((url, i) => ({
        businessType: 'oa_seal_apply_bill',
        businessId: 0,
        fileName: url.split('/').pop() || `file-${i}`,
        filePath: url,
        fileUrl: url,
        fileSize: 0,
      })),
    } as any);
    message.success('提交成功');
    emit('success');
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  applyLoginUser();
  if (formData.value.useType == null) formData.value.useType = 1;
  if (formData.value.useMode == null) formData.value.useMode = 1;
  if (formData.value.documentCount == null) formData.value.documentCount = 1;
  if (formData.value.isUrgent == null) formData.value.isUrgent = 0;
});

defineExpose({ reset, submit, getPredictVariables: () => ({}), submitting });
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
    <Form.Item label="印章" name="sealName">
      <Input v-model:value="formData.sealName" placeholder="请输入印章" />
    </Form.Item>
    <Form.Item label="用章类型" name="useType">
      <Select
        v-model:value="formData.useType"
        class="w-full"
        :options="getDictOptions(DICT_TYPE.OA_SEAL_USE_TYPE, 'number')"
        placeholder="请选择用章类型"
      />
    </Form.Item>
    <Form.Item label="用章方式" name="useMode">
      <Select
        v-model:value="formData.useMode"
        class="w-full"
        :options="getDictOptions(DICT_TYPE.OA_SEAL_USE_MODE, 'number')"
        placeholder="请选择用章方式"
      />
    </Form.Item>
    <Form.Item label="文件标题">
      <Input v-model:value="formData.documentTitle" placeholder="请输入文件标题" />
    </Form.Item>
    <Form.Item label="文件类型">
      <Input v-model:value="formData.documentType" placeholder="请输入文件类型" />
    </Form.Item>
    <Form.Item label="文件份数">
      <InputNumber
        v-model:value="formData.documentCount"
        class="w-full"
        :min="1"
      />
    </Form.Item>
    <Form.Item v-if="isContractUse" label="合同金额">
      <InputNumber
        v-model:value="formData.contractAmount"
        class="w-full"
        :min="0"
        :precision="2"
      />
    </Form.Item>
    <Form.Item v-if="isContractUse" label="合同对方">
      <Input v-model:value="formData.contractParty" placeholder="请输入合同对方" />
    </Form.Item>
    <Form.Item label="使用日期" name="expectedUseTime">
      <DatePicker
        v-model:value="formData.expectedUseTime"
        class="w-full"
        show-time
        format="YYYY-MM-DD HH:mm:ss"
      />
    </Form.Item>
    <Form.Item label="预计归还日期">
      <DatePicker
        v-model:value="formData.expectedReturnTime"
        class="w-full"
        show-time
        format="YYYY-MM-DD HH:mm:ss"
        placeholder="选填"
      />
    </Form.Item>
    <Form.Item label="是否紧急">
      <Select
        v-model:value="formData.isUrgent"
        class="w-full"
        :options="getDictOptions(DICT_TYPE.COMMON_STATUS, 'number')"
        placeholder="请选择是否紧急"
      />
    </Form.Item>
    <Form.Item label="用章事由" name="cause">
      <Input.TextArea
        v-model:value="formData.cause"
        :rows="3"
        placeholder="请输入用章事由"
      />
    </Form.Item>
    <Form.Item label="备注">
      <Input.TextArea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
    </Form.Item>
    <Form.Item label="附件">
      <FileUpload
        :value="formData.attachmentUrls || []"
        :max-number="10"
        :max-size="20"
        :multiple="true"
        @update:value="
          (v: string | string[]) => {
            formData.attachmentUrls = Array.isArray(v) ? v : v ? [v] : [];
          }
        "
      />
    </Form.Item>
  </Form>
</template>
