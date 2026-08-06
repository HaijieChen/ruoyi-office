<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceCustomerCompanyApi } from '#/api/finance/customer-company';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Checkbox, Form, Input, message } from 'ant-design-vue';

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
  isCustomer?: boolean;
  isSupplier?: boolean;
}

const formRef = ref();
const formData = ref<FormData>({
  isCustomer: true,
  isSupplier: false,
});

const supplierSelected = computed(() => !!formData.value.isSupplier);

const rules = computed<Record<string, Rule[]>>(() => ({
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  taxNo: [{ required: true, message: '纳税人识别号不能为空', trigger: 'blur' }],
  bankName: supplierSelected.value
    ? [{ required: true, message: '供应商须填写开户银行', trigger: 'blur' }]
    : [],
  bankAccount: supplierSelected.value
    ? [{ required: true, message: '供应商须填写银行账号', trigger: 'blur' }]
    : [],
}));

const isEdit = computed(() => !!formData.value?.id);
const getTitle = computed(() =>
  isEdit.value ? '编辑客商档案' : '新建客商档案',
);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number }>() || {};
    if (data.id) {
      const detail = await getCustomerCompany(data.id);
      formData.value = {
        ...detail,
        isCustomer: detail.isCustomer !== false,
        isSupplier: !!detail.isSupplier,
      };
    } else {
      formData.value = {
        isCustomer: true,
        isSupplier: false,
      };
    }
  },
  async onConfirm() {
    if (!formData.value.isCustomer && !formData.value.isSupplier) {
      message.error('至少选择客户或供应商角色之一');
      return;
    }
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
        isCustomer: !!formData.value.isCustomer,
        isSupplier: !!formData.value.isSupplier,
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
    formData.value = {
      isCustomer: true,
      isSupplier: false,
    };
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[640px]">
    <div class="mb-3 rounded bg-amber-50 px-3 py-2 text-sm text-amber-900">
      角色可叠加：客户用于开票/合同对方；供应商用于付款收款方（启用时须填开户行与账号）。税号租户内唯一。
    </div>
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <Form.Item label="名称" name="name" required>
        <Input v-model:value="formData.name" placeholder="往来单位全称" />
      </Form.Item>
      <Form.Item label="纳税人识别号" name="taxNo" required>
        <Input v-model:value="formData.taxNo" />
      </Form.Item>
      <Form.Item label="角色">
        <div class="flex gap-4">
          <Checkbox v-model:checked="formData.isCustomer">客户</Checkbox>
          <Checkbox v-model:checked="formData.isSupplier">供应商</Checkbox>
        </div>
      </Form.Item>
      <Form.Item
        label="开户银行"
        name="bankName"
        :required="supplierSelected"
      >
        <Input v-model:value="formData.bankName" />
      </Form.Item>
      <Form.Item
        label="银行账号"
        name="bankAccount"
        :required="supplierSelected"
      >
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
