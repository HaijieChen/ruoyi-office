<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceCompanyBankAccountApi } from '#/api/finance/company-bank-account';

import { computed, onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Form, Input, Select, message } from 'ant-design-vue';

import {
  createCompanyBankAccount,
  getCompanyBankAccount,
  updateCompanyBankAccount,
} from '#/api/finance/company-bank-account';
import { getSimpleDeptList } from '#/api/system/dept';

defineOptions({ name: 'FinanceCompanyBankAccountForm' });

const emit = defineEmits(['success']);

interface FormData {
  id?: number;
  entityCompanyDeptId?: number;
  accountName?: string;
  bankName?: string;
  accountHolder?: string;
  accountNo?: string;
  accountType?: string;
  currency?: string;
  remark?: string;
}

const formRef = ref();
const formData = ref<FormData>({ currency: 'CNY' });
const companyOptions = ref<{ label: string; value: number }[]>([]);

const rules: Record<string, Rule[]> = {
  entityCompanyDeptId: [{ required: true, message: '主体公司不能为空' }],
  accountName: [{ required: true, message: '账户名称不能为空' }],
  bankName: [{ required: true, message: '开户行不能为空' }],
  accountHolder: [{ required: true, message: '户名不能为空' }],
  accountNo: [{ required: true, message: '银行账号不能为空' }],
  currency: [{ required: true, message: '币种不能为空' }],
};

const isEdit = computed(() => !!formData.value?.id);
const getTitle = computed(() =>
  isEdit.value ? '编辑公司银行账户' : '新建公司银行账户',
);

onMounted(async () => {
  try {
    const list = await getSimpleDeptList();
    companyOptions.value = (list || [])
      .filter((d: any) => String(d.orgType) === '1')
      .map((d: any) => ({ label: d.name, value: d.id }));
  } catch {
    companyOptions.value = [];
  }
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number }>() || {};
    if (data.id) {
      const detail = await getCompanyBankAccount(data.id);
      formData.value = { ...detail };
    } else {
      formData.value = { currency: 'CNY' };
    }
  },
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const payload: FinanceCompanyBankAccountApi.SaveForm = {
        id: formData.value.id,
        entityCompanyDeptId: formData.value.entityCompanyDeptId!,
        accountName: formData.value.accountName!,
        bankName: formData.value.bankName!,
        accountHolder: formData.value.accountHolder!,
        accountNo: formData.value.accountNo!,
        accountType: formData.value.accountType,
        currency: formData.value.currency || 'CNY',
        remark: formData.value.remark,
      };
      if (isEdit.value) {
        await updateCompanyBankAccount(payload);
        message.success('更新成功');
      } else {
        await createCompanyBankAccount(payload);
        message.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  onClosed() {
    formData.value = { currency: 'CNY' };
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
          placeholder="选择组织架构中的公司"
        />
      </Form.Item>
      <Form.Item label="账户名称" name="accountName" required>
        <Input v-model:value="formData.accountName" />
      </Form.Item>
      <Form.Item label="开户行" name="bankName" required>
        <Input v-model:value="formData.bankName" />
      </Form.Item>
      <Form.Item label="户名" name="accountHolder" required>
        <Input v-model:value="formData.accountHolder" />
      </Form.Item>
      <Form.Item label="银行账号" name="accountNo" required>
        <Input v-model:value="formData.accountNo" />
      </Form.Item>
      <Form.Item label="账户类型" name="accountType">
        <Select
          v-model:value="formData.accountType"
          allow-clear
          :options="[
            { label: '基本户', value: 'BASIC' },
            { label: '一般户', value: 'GENERAL' },
            { label: '专用户', value: 'SPECIAL' },
          ]"
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
      <Form.Item label="备注" name="remark">
        <Input.TextArea v-model:value="formData.remark" :rows="2" />
      </Form.Item>
    </Form>
  </Modal>
</template>
