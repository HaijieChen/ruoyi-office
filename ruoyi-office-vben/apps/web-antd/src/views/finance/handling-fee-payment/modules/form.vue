<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceCompanyBankAccountApi } from '#/api/finance/company-bank-account';
import type { FinanceHandlingFeePaymentApi } from '#/api/finance/handling-fee-payment';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';
import {
  createHandlingFeePayment,
  getHandlingFeePayment,
  updateHandlingFeePayment,
} from '#/api/finance/handling-fee-payment';
import { getSimpleCompanyList } from '#/api/system/dept';

defineOptions({ name: 'FinanceHandlingFeePaymentForm' });

const emit = defineEmits(['success']);

interface FormData {
  id?: number;
  entityCompanyDeptId?: number;
  companyBankAccountId?: number;
  feeDate?: string;
  amount?: number;
  currency?: string;
}

const formRef = ref();
const formData = ref<FormData>({});
const companyOptions = ref<{ label: string; value: number }[]>([]);
const accountList = ref<FinanceCompanyBankAccountApi.Account[]>([]);
const hydrating = ref(false);

const accountOptions = computed(() =>
  accountList.value.map((a) => ({
    label: [a.accountName, a.bankName, a.accountNoMasked || a.accountNo]
      .filter(Boolean)
      .join(' '),
    value: a.id,
  })),
);

const selectedAccount = computed(() =>
  accountList.value.find((a) => a.id === formData.value.companyBankAccountId),
);

const rules: Record<string, Rule[]> = {
  entityCompanyDeptId: [{ required: true, message: '主体公司不能为空' }],
  companyBankAccountId: [{ required: true, message: '公司银行账户不能为空' }],
  feeDate: [{ required: true, message: '付款日期不能为空' }],
  amount: [
    { required: true, message: '金额不能为空' },
    {
      validator: (_: Rule, v: number) =>
        v > 0 ? Promise.resolve() : Promise.reject('手续费金额必须大于 0'),
      trigger: 'change',
    },
  ],
  currency: [{ required: true, message: '币种不能为空' }],
};

const isEdit = computed(() => !!formData.value?.id);
const getTitle = computed(() =>
  isEdit.value ? '编辑手续费付款' : '新建手续费付款',
);

function toDateString(value: unknown): string | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  if (typeof value === 'string') {
    return value.slice(0, 10);
  }
  if (typeof value === 'number') {
    return dayjs(value).format('YYYY-MM-DD');
  }
  if (Array.isArray(value) && value.length >= 3) {
    const [y, m, d] = value as number[];
    return dayjs(new Date(y, m - 1, d)).format('YYYY-MM-DD');
  }
  const parsed = dayjs(value as string | number | Date);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : undefined;
}

async function loadCompanyOptions() {
  try {
    const list = (await getSimpleCompanyList()) || [];
    companyOptions.value = list
      .filter((c) => c.id != null)
      .map((c) => ({ label: c.name, value: c.id as number }));
  } catch {
    companyOptions.value = [];
  }
}

async function loadAccounts(entityCompanyDeptId?: number) {
  if (!entityCompanyDeptId) {
    accountList.value = [];
    return;
  }
  try {
    accountList.value =
      (await getCompanyBankAccountSimpleList(entityCompanyDeptId)) || [];
  } catch {
    accountList.value = [];
  }
}

watch(
  () => formData.value.entityCompanyDeptId,
  async (id) => {
    if (hydrating.value) {
      return;
    }
    formData.value.companyBankAccountId = undefined;
    await loadAccounts(id);
  },
);

watch(
  () => formData.value.companyBankAccountId,
  (id) => {
    if (hydrating.value || !id) {
      return;
    }
    const account = accountList.value.find((a) => a.id === id);
    formData.value.currency = account?.currency || 'CNY';
  },
);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    await loadCompanyOptions();
    const data = modalApi.getData<{ id?: number }>() || {};
    if (data.id) {
      hydrating.value = true;
      try {
        const detail = await getHandlingFeePayment(data.id);
        formData.value = {
          id: detail.id,
          entityCompanyDeptId: detail.entityCompanyDeptId,
          companyBankAccountId: detail.companyBankAccountId,
          feeDate: toDateString(detail.feeDate),
          amount: Number(detail.amount),
          currency: detail.currency,
        };
        await loadAccounts(detail.entityCompanyDeptId);
        if (
          detail.companyBankAccountId != null &&
          !accountList.value.some((a) => a.id === detail.companyBankAccountId)
        ) {
          accountList.value = [
            ...accountList.value,
            {
              id: detail.companyBankAccountId,
              entityCompanyDeptId: detail.entityCompanyDeptId,
              accountName: detail.accountName || '',
              bankName: detail.bankName || '',
              accountHolder: detail.accountName || '',
              accountNo: detail.accountNo,
              accountNoMasked: detail.accountNoMasked,
              currency: detail.currency,
            },
          ];
        }
      } finally {
        hydrating.value = false;
      }
    } else {
      formData.value = {};
      accountList.value = [];
    }
  },
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const payload: FinanceHandlingFeePaymentApi.SaveForm = {
        id: formData.value.id,
        entityCompanyDeptId: formData.value.entityCompanyDeptId!,
        companyBankAccountId: formData.value.companyBankAccountId!,
        feeDate: formData.value.feeDate!,
        amount: formData.value.amount!,
        currency: formData.value.currency!,
      };
      if (isEdit.value) {
        await updateHandlingFeePayment(payload);
        message.success('更新成功');
      } else {
        await createHandlingFeePayment(payload);
        message.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  onClosed() {
    formData.value = {};
    accountList.value = [];
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[560px]">
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <Form.Item label="主体公司" name="entityCompanyDeptId" required>
        <Select
          v-model:value="formData.entityCompanyDeptId"
          :options="companyOptions"
          show-search
          option-filter-prop="label"
          placeholder="选择主体公司"
        />
      </Form.Item>
      <Form.Item label="银行账户" name="companyBankAccountId" required>
        <Select
          v-model:value="formData.companyBankAccountId"
          :options="accountOptions"
          :disabled="!formData.entityCompanyDeptId"
          show-search
          option-filter-prop="label"
          placeholder="选择公司银行账户"
        />
      </Form.Item>
      <Form.Item label="户名">
        <Input
          :value="selectedAccount?.accountHolder"
          disabled
        />
      </Form.Item>
      <Form.Item label="开户行">
        <Input :value="selectedAccount?.bankName" disabled />
      </Form.Item>
      <Form.Item label="账号">
        <Input
          :value="
            selectedAccount?.accountNoMasked || selectedAccount?.accountNo
          "
          disabled
        />
      </Form.Item>
      <Form.Item label="付款日期" name="feeDate" required>
        <DatePicker
          v-model:value="formData.feeDate"
          class="w-full"
          value-format="YYYY-MM-DD"
          placeholder="请选择付款日期"
        />
      </Form.Item>
      <Form.Item label="金额" name="amount" required>
        <InputNumber
          v-model:value="formData.amount"
          :min="0.01"
          :precision="2"
          class="w-full"
          placeholder="请输入金额"
        />
      </Form.Item>
      <Form.Item label="币种" name="currency" required>
        <Select
          v-model:value="formData.currency"
          :options="[
            { label: 'CNY', value: 'CNY' },
            { label: 'USD', value: 'USD' },
            { label: 'HKD', value: 'HKD' },
          ]"
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
