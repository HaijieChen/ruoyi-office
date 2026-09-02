<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceContractApplicationApi } from '#/api/finance/contract-application';
import type { FinanceCustomerCompanyApi } from '#/api/finance/customer-company';

import { computed, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
  Textarea,
} from 'ant-design-vue';

import {
  createAndStartContractApplication,
  getContractApplication,
  resubmitContractApplication,
  updateContractApplication,
} from '#/api/finance/contract-application';
import { getCustomerCompanySimpleList } from '#/api/finance/customer-company';
import { FileUpload } from '#/components/upload';
import { getSimpleCompanyList } from '#/api/system/dept';
import { defaultCurrencyFromCompanyAccounts } from '#/views/finance/shared/account-currency';
import { useBusinessStaffField } from '#/views/finance/shared/use-business-staff';

defineOptions({ name: 'FinanceContractApplicationFormBody' });

const emit = defineEmits<{
  predictChange: [vars: Record<string, unknown>];
  success: [];
}>();

const FILE_TYPE_OPTIONS = [
  { label: '采购合同', value: '采购合同' },
  { label: '销售合同', value: '销售合同' },
  { label: '付款业务合同', value: '付款业务合同' },
  { label: '租赁合同', value: '租赁合同' },
  { label: '借款合同', value: '借款合同' },
  { label: '推广充值业务合同', value: '推广充值业务合同' },
  { label: '其他', value: '其他' },
];

const SETTLEMENT_OPTIONS = [
  { label: 'CPA', value: 'CPA' },
  { label: 'CPS', value: 'CPS' },
  { label: 'CPC', value: 'CPC' },
  { label: '月结', value: '月结' },
  { label: '其他', value: '其他' },
];

const productTypeOptions = computed(() =>
  getDictOptions(DICT_TYPE.FINANCE_PRODUCT_TYPE).map((d) => ({
    label: d.label,
    value: d.value as number | string,
  })),
);

interface FormData {
  id?: number;
  mode?: 'create' | 'resubmit' | 'edit';
  counterpartyCompanyId?: number;
  amountNa?: boolean;
  contractAmount?: number;
  entityCompanyDeptId?: number;
  currency?: string;
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
  businessStaffUserId?: number;
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
const submitting = ref(false);
const { staffOptions, loadBusinessStaff } = useBusinessStaffField();

const isResubmit = computed(() => formData.value.mode === 'resubmit');
const isEdit = computed(() => formData.value.mode === 'edit');
const isSalesLikeContract = computed(
  () =>
    formData.value.fileType === '销售合同' ||
    formData.value.fileType === '付款业务合同',
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
  entityCompanyDeptId: [
    { required: true, message: '请选择主体公司', trigger: 'change' },
  ],
  currency: [
    {
      validator: async () => {
        if (formData.value.amountNa) return;
        const c = (formData.value.currency || '').toUpperCase();
        if (!['CNY', 'USD', 'HKD'].includes(c)) {
          throw new Error('请选择币种 CNY/USD/HKD');
        }
      },
      trigger: 'change',
    },
  ],
  fileName: [
    { required: true, message: '请先上传电子版文件', trigger: 'change' },
  ],
  fileType: [{ required: true, message: '请选择合同类型', trigger: 'change' }],
  productType: isSalesLikeContract.value
    ? [{ required: true, message: '请选择产品类型', trigger: 'change' }]
    : [],
  rebateRatio: isSalesLikeContract.value
    ? [{ required: true, message: '请输入返点比例', trigger: 'blur' }]
    : [],
  settlementMethod: isSalesLikeContract.value
    ? [{ required: true, message: '请选择结算方式', trigger: 'change' }]
    : [],
  copyCount: [{ required: true, message: '请输入文件份数', trigger: 'change' }],
  sealTypes: [{ required: true, message: '请输入印章类型', trigger: 'blur' }],
  draftFileUrl: [
    { required: true, message: '请上传电子版文件或填写 URL', trigger: 'change' },
  ],
  contractAmount: [
    {
      validator: async () => {
        if (formData.value.amountNa) return;
        const amt = formData.value.contractAmount;
        if (amt === null || amt === undefined || Number(amt) <= 0) {
          throw new Error('未勾选金额不适用时，合同金额必须大于 0');
        }
      },
      trigger: 'change',
    },
  ],
  mailAddress: [
    {
      validator: async () => {
        if (!formData.value.needMail) return;
        if (!formData.value.mailAddress?.trim()) {
          throw new Error('需要邮寄时必须填写邮寄地址');
        }
      },
      trigger: 'blur',
    },
  ],
  preProcessRef: [
    {
      validator: async () => {
        if (!needPreProcess.value) return;
        if (!formData.value.preProcessRef?.trim()) {
          throw new Error('采购/租赁合同必须填写前置流程');
        }
      },
      trigger: 'blur',
    },
  ],
}));

function fileNameFromUrl(url?: string) {
  if (!url) return undefined;
  const raw = String(url).split('?')[0] || '';
  const name = decodeURIComponent(
    raw.slice(Math.max(0, raw.lastIndexOf('/') + 1)),
  );
  return name || undefined;
}

function onDraftFile(val: string | string[]) {
  const url = Array.isArray(val) ? String(val[0] || '') : String(val || '');
  formData.value.draftFileUrl = url || undefined;
  formData.value.fileName = fileNameFromUrl(url);
}

function onDraftUploaded(payload: { name?: string; url?: string }) {
  if (payload?.url) {
    formData.value.draftFileUrl = payload.url;
  }
  if (payload?.name) {
    formData.value.fileName = payload.name;
  }
}

function clearForm() {
  formData.value = {
    amountNa: false,
    needMail: false,
    copyCount: 1,
    mode: 'create',
    businessStaffUserId: undefined,
  };
  formRef.value?.resetFields?.();
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
      .filter((c) => c.id != null && c.name)
      .map((c) => ({
        value: c.id as number,
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
    currency: formData.value.amountNa
      ? formData.value.currency
      : (formData.value.currency || 'CNY').toUpperCase(),
    entityCompanyDeptId: formData.value.entityCompanyDeptId!,
    fileName: formData.value.fileName!,
    fileType: formData.value.fileType!,
    productType: isSalesLikeContract.value ? formData.value.productType : undefined,
    rebateRatio: isSalesLikeContract.value ? formData.value.rebateRatio : undefined,
    settlementMethod: isSalesLikeContract.value
      ? formData.value.settlementMethod
      : undefined,
    copyCount: formData.value.copyCount!,
    sealTypes: formData.value.sealTypes!,
    needMail: !!formData.value.needMail,
    mailAddress: formData.value.mailAddress,
    preProcessRef: formData.value.preProcessRef,
    startDate: formData.value.startDate,
    endDate: formData.value.endDate,
    draftFileUrl: formData.value.draftFileUrl!,
    remark: formData.value.remark,
    businessStaffUserId: formData.value.businessStaffUserId,
  };
}

function onFileTypeChange() {
  if (!isSalesLikeContract.value) {
    formData.value.productType = undefined;
    formData.value.rebateRatio = undefined;
    formData.value.settlementMethod = undefined;
  }
  emit('predictChange', getPredictVariables());
}

function getPredictVariables(): Record<string, unknown> {
  const vars: Record<string, unknown> = {};
  if (
    !formData.value.amountNa &&
    formData.value.contractAmount !== null &&
    formData.value.contractAmount !== undefined &&
    Number.isFinite(Number(formData.value.contractAmount))
  ) {
    vars.contractAmount = Number(formData.value.contractAmount);
  }
  if (formData.value.fileType) {
    vars.fileType = formData.value.fileType;
  }
  return vars;
}

async function reset(opts?: { id?: number; mode?: string }) {
  const defaultStaff = await loadBusinessStaff();
  await Promise.all([loadCustomers(), loadCompanies()]);
  if ((opts?.mode === 'resubmit' || opts?.mode === 'edit') && opts.id) {
    const detail = await getContractApplication(opts.id);
    formData.value = {
      id: detail.id,
      mode: opts.mode === 'edit' ? 'edit' : 'resubmit',
      counterpartyCompanyId: detail.counterpartyCompanyId,
      amountNa: !!detail.amountNa,
      contractAmount: detail.contractAmount,
      currency: detail.currency || 'CNY',
      entityCompanyDeptId: detail.entityCompanyDeptId,
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
      businessStaffUserId: detail.businessStaffUserId ?? defaultStaff,
    };
  } else {
    clearForm();
    formData.value.businessStaffUserId = defaultStaff;
  }
  emit('predictChange', getPredictVariables());
}

interface SubmitContext {
  startUserSelectAssignees?: Record<string, number[]>;
  startCompanyDeptId?: number; startDeptId?: number;
}

async function submit(ctx?: SubmitContext): Promise<void> {
  await formRef.value?.validate();
  submitting.value = true;
  try {
    const payload: FinanceContractApplicationApi.CreateAndStartRequest = {
      ...buildPayload(),
      startUserSelectAssignees: ctx?.startUserSelectAssignees,
      startCompanyDeptId: ctx?.startCompanyDeptId,
      startDeptId: ctx?.startDeptId,
    };
    if (isEdit.value && formData.value.id) {
      await updateContractApplication(formData.value.id, payload);
      message.success('已保存');
    } else {
      await (isResubmit.value && formData.value.id
        ? resubmitContractApplication(formData.value.id, payload)
        : createAndStartContractApplication(payload));
      message.success('提交成功');
    }
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
    :label-col="{ span: 7 }"
    :wrapper-col="{ span: 15 }"
  >
    <Form.Item label="主体公司" name="entityCompanyDeptId">
      <Select
        v-model:value="formData.entityCompanyDeptId"
        class="w-full"
        show-search
        option-filter-prop="label"
        :loading="loadingCompany"
        :options="companyOptions"
        placeholder="请选择主体公司"
        @change="(v: any) => {
          if (formData.amountNa) {
            return;
          }
          void defaultCurrencyFromCompanyAccounts(Number(v)).then((currency) => {
            if (!formData.amountNa) {
              formData.currency = currency;
            }
          });
        }"
      />
    </Form.Item>
    <Form.Item label="业务人员" name="businessStaffUserId">
      <Select
        v-model:value="formData.businessStaffUserId"
        class="w-full"
        show-search
        option-filter-prop="label"
        :options="staffOptions"
        placeholder="默认提单人，可改选任职公司员工"
      />
    </Form.Item>
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
    <Form.Item v-if="!formData.amountNa" label="合同金额" name="contractAmount">
      <InputNumber
        v-model:value="formData.contractAmount"
        class="w-full"
        :min="0.01"
        :precision="2"
        placeholder="请输入合同金额"
        @change="emit(predictChange, getPredictVariables())"
      />
    </Form.Item>
    <Form.Item label="起始日期" name="startDate" class="mb-2">
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
    <Form.Item v-if="!formData.amountNa" label="币种" name="currency">
      <Select
        v-model:value="formData.currency"
        class="w-full"
        :options="[
          { label: '人民币 CNY', value: 'CNY' },
          { label: '美元 USD', value: 'USD' },
          { label: '港币 HKD', value: 'HKD' },
        ]"
        placeholder="CNY/USD/HKD"
      />
    </Form.Item>
    <Form.Item label="合同类型" name="fileType">
      <Select
        v-model:value="formData.fileType"
        class="w-full"
        :options="FILE_TYPE_OPTIONS"
        placeholder="请选择"
        @change="onFileTypeChange"
      />
    </Form.Item>
    <Form.Item v-if="needPreProcess" label="前置流程" name="preProcessRef">
      <Input
        v-model:value="formData.preProcessRef"
        placeholder="采购/租赁须关联前置流程"
      />
    </Form.Item>
    <Form.Item v-if="isSalesLikeContract" label="产品类型" name="productType" required>
      <Select
        v-model:value="formData.productType"
        class="w-full"
        :options="productTypeOptions"
        placeholder="请选择产品类型"
      />
    </Form.Item>
    <Form.Item v-if="isSalesLikeContract" label="返点比例" name="rebateRatio">
      <Input v-model:value="formData.rebateRatio" placeholder="文本" />
    </Form.Item>
    <Form.Item v-if="isSalesLikeContract" label="结算方式" name="settlementMethod">
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
        placeholder="如合同专用章，或逗号分隔"
      />
    </Form.Item>
    <Form.Item label="需要邮寄" name="needMail">
      <Switch v-model:checked="formData.needMail" />
    </Form.Item>
    <Form.Item v-if="formData.needMail" label="邮寄地址" name="mailAddress">
      <Input v-model:value="formData.mailAddress" placeholder="邮寄地址" />
    </Form.Item>
    <Form.Item label="电子版文件" name="draftFileUrl">
      <FileUpload
        :value="formData.draftFileUrl ? [formData.draftFileUrl] : []"
        :max-number="1"
        :max-size="20"
        help-text="上传用印文件电子版"
        @update:value="onDraftFile"
        @uploaded="onDraftUploaded"
      />
      <Input
        class="mt-2"
        v-model:value="formData.draftFileUrl"
        placeholder="也可直接填写文件 URL"
        @change="onDraftFile(formData.draftFileUrl || '')"
      />
      <div v-if="formData.fileName" class="mt-1 text-sm text-gray-500">
        文件名称：{{ formData.fileName }}
      </div>
    </Form.Item>
    <Form.Item label="备注" name="remark">
      <Textarea v-model:value="formData.remark" :rows="2" />
    </Form.Item>
  </Form>
</template>
