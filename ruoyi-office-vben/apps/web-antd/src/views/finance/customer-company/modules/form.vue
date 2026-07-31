<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceCustomerCompanyApi } from '#/api/finance/customer-company';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Form, Input, message } from 'ant-design-vue';

import {
  createCustomerCompany,
  getCustomerCompany,
  updateCustomerCompany,
} from '#/api/finance/customer-company';

defineOptions({ name: 'FinanceCustomerCompanyForm' });

const emit = defineEmits(['success']);

interface FormData {
  id?: number;
  name?: string;
  taxNo?: string;
  bankName?: string;
  bankAccount?: string;
  address?: string;
  phone?: string;
  contactName?: string;
  email?: string;
}

const formRef = ref();
const formData = ref<FormData>({});

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  taxNo: [{ required: true, message: '纳税人识别号不能为空', trigger: 'blur' }],
};

const isEdit = computed(() => !!formData.value?.id);
const getTitle = computed(() =>
  isEdit.value ? '编辑客户公司' : '新建客户公司',
);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number }>() || {};
    if (data.id) {
      const detail = await getCustomerCompany(data.id);
      formData.value = { ...detail };
    } else {
      formData.value = {};
    }
  },
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const payload: FinanceCustomerCompanyApi.SaveForm = {
        id: formData.value.id,
        name: formData.value.name!,
        taxNo: formData.value.taxNo!,
        bankName: formData.value.bankName,
        bankAccount: formData.value.bankAccount,
        address: formData.value.address,
        phone: formData.value.phone,
        contactName: formData.value.contactName,
        email: formData.value.email,
      };
      if (isEdit.value) {
        await updateCustomerCompany(payload);
        message.success('更新成功');
      } else {
        await createCustomerCompany(payload);
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
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[640px]">
    <div class="mb-3 rounded bg-amber-50 px-3 py-2 text-sm text-amber-900">
      启用仅需名称与税号；开户行/地址/电话可空。开票选用后税项只读，修改请在本档案维护。
    </div>
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <Form.Item label="名称" name="name" required>
        <Input v-model:value="formData.name" placeholder="购方公司全称" />
      </Form.Item>
      <Form.Item label="纳税人识别号" name="taxNo" required>
        <Input v-model:value="formData.taxNo" />
      </Form.Item>
      <Form.Item label="开户银行" name="bankName">
        <Input v-model:value="formData.bankName" />
      </Form.Item>
      <Form.Item label="银行账号" name="bankAccount">
        <Input v-model:value="formData.bankAccount" />
      </Form.Item>
      <Form.Item label="邮寄地址" name="address">
        <Input v-model:value="formData.address" />
      </Form.Item>
      <Form.Item label="联系电话" name="phone">
        <Input v-model:value="formData.phone" />
      </Form.Item>
      <Form.Item label="联系人" name="contactName">
        <Input v-model:value="formData.contactName" />
      </Form.Item>
      <Form.Item label="邮箱" name="email">
        <Input v-model:value="formData.email" />
      </Form.Item>
    </Form>
  </Modal>
</template>
