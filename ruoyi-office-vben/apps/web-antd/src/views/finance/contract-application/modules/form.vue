<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceCustomerCompanyApi } from '#/api/finance/customer-company';
import type { FinanceContractApplicationApi } from '#/api/finance/contract-application';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Textarea,
  message,
} from 'ant-design-vue';

import { getCustomerCompanySimpleList } from '#/api/finance/customer-company';
import {
  createAndStartContractApplication,
  getContractApplication,
  resubmitContractApplication,
} from '#/api/finance/contract-application';
import { getSimpleCompanyList } from '#/api/system/dept';

defineOptions({ name: 'FinanceContractApplicationForm' });

const emit = defineEmits(['success']);

const FILE_TYPE_OPTIONS = [
  { label: '采购合同', value: '采购合同' },
  { label: '销售合同', value: '销售合同' },
  { label: '租赁合同', value: '租赁合同' },
  { label: '借款合同', value: '借款合同' },
  { label: '推广充值业务合同', value: '推广充值业务合同' },
];

const SETTLEMENT_OPTIONS = [
  { label: 'CPA', value: 'CPA' },
  { label: 'CPS', value: 'CPS' },
  { label: 'CPC', value: 'CPC' },
  { label: '月结', value: '月结' },
  { label: '其他', value: '其他' },
];

interface FormData {
  id?: number;
  mode?: 'create' | 'resubmit';
  counterpartyCompanyId?: number;
  amountNa?: boolean;
  contractAmount?: number;
  signCompany?: string;
  fileName?: string;
  fileType?: string;
  productType?: string;
  rebateRatio?: string;
  settlementMethod?: string;
  copyCount?: number;
  sealTypes?: string;
  needMail?: boolean;
  mailAddress?: string;
  preProcessRef?: string;
  startDate?: string;
  endDate?: string;
  draftFileUrl?: string;
  remark?: string;
}

interface Option {
  label: string;
  value: number | string;
}

const formRef = ref();
const formData = ref<FormData>({
  amountNa: false,
  needMail: false,
  copyCount: 1,
});
const customerOptions = ref<Option[]>([]);
const companyOptions = ref<Option[]>([]);
const loadingCustomer = ref(false);
const loadingCompany = ref(false);

const isResubmit = computed(() => formData.value.mode === 'resubmit');
const getTitle = computed(() =>
  isResubmit.value ? '驳回后重提合同签约' : '提交合同签约申请（无草稿）',
);
const needPreProcess = computed(
  () =>
    formData.value.fileType === '采购合同' ||
    formData.value.fileType === '租赁合同',
);

const rules = computed<Record<string, Rule[]>>(() => ({
  counterpartyCompanyId: [
    { required: true, message: '请选择对方客商', trigger: 'change' },
  ],
  signCompany: [{ required: true, message: '请选择签约主体', trigger: 'change' }],
  fileName: [{ required: true, message: '请输入用印文件名称', trigger: 'blur' }],
  fileType: [{ required: true, message: '请选择文件类型', trigger: 'change' }],
  rebateRatio: [{ required: true, message: '请输入返点比例', trigger: 'blur' }],
  settlementMethod: [
    { required: true, message: '请选择结算方式', trigger: 'change' },
  ],
  copyCount: [{ required: true, message: '请输入文件份数', trigger: 'change' }],
  sealTypes: [{ required: true, message: '请输入印章类型', trigger: 'blur' }],
  draftFileUrl: [
    { required: true, message: '请填写用印文件电子版 URL', trigger: 'blur' },
  ],
  contractAmount: [
    {
      validator: async () => {
        if (formData.value.amountNa) return Promise.resolve();
        const amt = formData.value.contractAmount;
        if (amt == null || Number(amt) <= 0) {
          return Promise.reject('未勾选金额不适用时，合同金额必须大于 0');
        }
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
  mailAddress: [
    {
      validator: async () => {
        if (!formData.value.needMail) return Promise.resolve();
        if (!formData.value.mailAddress?.trim()) {
          return Promise.reject('需要邮寄时必须填写邮寄地址');
        }
        return Promise.resolve();
      },
      trigger: 'blur',
    },
  ],
  preProcessRef: [
    {
      validator: async () => {
        if (!needPreProcess.value) return Promise.resolve();
        if (!formData.value.preProcessRef?.trim()) {
          return Promise.reject('采购/租赁合同必须填写前置流程');
        }
        return Promise.resolve();
      },
      trigger: 'blur',
    },
  ],
}));

function resetForm() {
  formData.value = {
    amountNa: false,
    needMail: false,
    copyCount: 1,
  };
  formRef.value?.resetFields();
}

async function loadCustomers() {
  loadingCustomer.value = true;
  try {
    const list = (await getCustomerCompanySimpleList()) || [];
    customerOptions.value = list.map(
      (c: FinanceCustomerCompanyApi.CustomerCompany) => ({
        value: c.id,
        label: `${c.name}${c.taxNo ? ` (${c.taxNo})` : ''}`,
      }),
    );
  } finally {
    loadingCustomer.value = false;
  }
}

async function loadCompanies() {
  loadingCompany.value = true;
  try {
    const list = (await getSimpleCompanyList()) || [];
    companyOptions.value = list
      .filter((c) => c.name)
      .map((c) => ({
        value: c.name as string,
        label: c.name as string,
      }));
  } finally {
    loadingCompany.value = false;
  }
}

function buildPayload(): FinanceContractApplicationApi.CreateAndStartRequest {
  return {
    counterpartyCompanyId: formData.value.counterpartyCompanyId!,
    amountNa: !!formData.value.amountNa,
    contractAmount: formData.value.amountNa
      ? undefined
      : formData.value.contractAmount,
    signCompany: formData.value.signCompany!,
    fileName: formData.value.fileName!,
    fileType: formData.value.fileType!,
    productType: formData.value.productType,
    rebateRatio: formData.value.rebateRatio!,
    settlementMethod: formData.value.settlementMethod!,
    copyCount: formData.value.copyCount!,
    sealTypes: formData.value.sealTypes!,
    needMail: !!formData.value.needMail,
    mailAddress: formData.value.mailAddress,
    preProcessRef: formData.value.preProcessRef,
    startDate: formData.value.startDate,
    endDate: formData.value.endDate,
    draftFileUrl: formData.value.draftFileUrl!,
    remark: formData.value.remark,
  };
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const payload = buildPayload();
      if (isResubmit.value && formData.value.id) {
        await resubmitContractApplication(formData.value.id, payload);
      } else {
        await createAndStartContractApplication(payload);
      }
      await modalApi.close();
      emit('success');
      message.success('提交成功');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      resetForm();
      return;
    }
    await Promise.all([loadCustomers(), loadCompanies()]);
    const data = modalApi.getData<{ id?: number; mode?: string }>();
    if (data?.mode === 'resubmit' && data.id) {
      modalApi.lock();
      try {
        const detail = await getContractApplication(data.id);
        formData.value = {
          id: detail.id,
          mode: 'resubmit',
          counterpartyCompanyId: detail.counterpartyCompanyId,
          amountNa: !!detail.amountNa,
          contractAmount: detail.contractAmount,
          signCompany: detail.signCompany,
          fileName: detail.fileName,
          fileType: detail.fileType,
          productType: detail.productType,
          rebateRatio: detail.rebateRatio,
          settlementMethod: detail.settlementMethod,
          copyCount: detail.copyCount ?? 1,
          sealTypes: detail.sealTypes,
          needMail: !!detail.needMail,
          mailAddress: detail.mailAddress,
          preProcessRef: detail.preProcessRef,
          startDate: detail.startDate?.slice?.(0, 10) || detail.startDate,
          endDate: detail.endDate?.slice?.(0, 10) || detail.endDate,
          draftFileUrl: detail.draftFileUrl,
          remark: detail.remark,
        };
      } finally {
        modalApi.unlock();
      }
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[720px]">
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 7 }"
      :wrapper-col="{ span: 15 }"
    >
      <Form.Item label="对方客商" name="counterpartyCompanyId">
        <Select
          v-model:value="formData.counterpartyCompanyId"
          class="w-full"
          show-search
          option-filter-prop="label"
          :loading="loadingCustomer"
          :options="customerOptions"
          placeholder="请选择启用中的客商公司"
        />
      </Form.Item>
      <Form.Item label="金额不适用" name="amountNa">
        <Switch v-model:checked="formData.amountNa" />
      </Form.Item>
      <Form.Item
        v-if="!formData.amountNa"
        label="合同金额"
        name="contractAmount"
      >
        <InputNumber
          v-model:value="formData.contractAmount"
          class="w-full"
          :min="0.01"
          :precision="2"
          placeholder="请输入合同金额"
        />
      </Form.Item>
      <Form.Item label="签约主体" name="signCompany">
        <Select
          v-model:value="formData.signCompany"
          class="w-full"
          show-search
          option-filter-prop="label"
          :loading="loadingCompany"
          :options="companyOptions"
          placeholder="请选择签约主体公司"
        />
      </Form.Item>
      <Form.Item label="用印文件名称" name="fileName">
        <Input v-model:value="formData.fileName" placeholder="完整准确的文件名称" />
      </Form.Item>
      <Form.Item label="文件类型" name="fileType">
        <Select
          v-model:value="formData.fileType"
          class="w-full"
          :options="FILE_TYPE_OPTIONS"
          placeholder="请选择"
        />
      </Form.Item>
      <Form.Item
        v-if="needPreProcess"
        label="前置流程"
        name="preProcessRef"
      >
        <Input
          v-model:value="formData.preProcessRef"
          placeholder="采购/租赁须关联前置流程"
        />
      </Form.Item>
      <Form.Item label="产品类型" name="productType">
        <Input v-model:value="formData.productType" placeholder="如红书、百度" />
      </Form.Item>
      <Form.Item label="返点比例" name="rebateRatio">
        <Input v-model:value="formData.rebateRatio" placeholder="文本" />
      </Form.Item>
      <Form.Item label="结算方式" name="settlementMethod">
        <Select
          v-model:value="formData.settlementMethod"
          class="w-full"
          :options="SETTLEMENT_OPTIONS"
          placeholder="请选择"
        />
      </Form.Item>
      <Form.Item label="文件份数" name="copyCount">
        <InputNumber v-model:value="formData.copyCount" class="w-full" :min="1" />
      </Form.Item>
      <Form.Item label="印章类型" name="sealTypes">
        <Input
          v-model:value="formData.sealTypes"
          placeholder='如 ["合同专用章"] 或逗号分隔'
        />
      </Form.Item>
      <Form.Item label="需要邮寄" name="needMail">
        <Switch v-model:checked="formData.needMail" />
      </Form.Item>
      <Form.Item v-if="formData.needMail" label="邮寄地址" name="mailAddress">
        <Input v-model:value="formData.mailAddress" placeholder="邮寄地址" />
      </Form.Item>
      <Form.Item label="起始日期" name="startDate">
        <DatePicker
          v-model:value="formData.startDate"
          value-format="YYYY-MM-DD"
          class="w-full"
        />
      </Form.Item>
      <Form.Item label="结束日期" name="endDate">
        <DatePicker
          v-model:value="formData.endDate"
          value-format="YYYY-MM-DD"
          class="w-full"
        />
      </Form.Item>
      <Form.Item label="电子版文件URL" name="draftFileUrl">
        <Input
          v-model:value="formData.draftFileUrl"
          placeholder="用印文件电子版附件地址"
        />
      </Form.Item>
      <Form.Item label="备注" name="remark">
        <Textarea v-model:value="formData.remark" :rows="2" />
      </Form.Item>
      <div
        v-if="formData.fileType === '推广充值业务合同'"
        class="mb-2 text-amber-600 text-sm pl-[29%]"
      >
        提示：本流程不管理充值框架余额/返点引擎，仅作签约台账。
      </div>
    </Form>
  </Modal>
</template>
