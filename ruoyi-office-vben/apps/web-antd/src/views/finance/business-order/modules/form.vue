<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceBusinessOrderApi } from '#/api/finance/business-order';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Form,
  Input,
  InputNumber,
  message,
  Select,
  Textarea,
} from 'ant-design-vue';

import {
  createBusinessOrder,
  getBusinessOrder,
  updateBusinessOrder,
} from '#/api/finance/business-order';
import { $t } from '#/locales';

import { ORDER_STATUS_OPTIONS } from '../data';

defineOptions({ name: 'FinanceBusinessOrderForm' });

const emit = defineEmits(['success']);

type FormData = Partial<FinanceBusinessOrderApi.SaveForm> & { id?: number };

const formRef = ref();
const formData = ref<FormData>({
  id: undefined,
  orderNo: undefined,
  businessSubject: undefined,
  businessType: undefined,
  contractRef: undefined,
  projectRef: undefined,
  receivableAmount: 0,
  payableAmount: 0,
  currency: 'CNY',
  ownerId: undefined,
  status: 0,
  remark: undefined,
});

const rules: Record<string, Rule[]> = {
  orderNo: [{ required: true, message: '订单编号不能为空', trigger: 'blur' }],
  businessSubject: [{ required: true, message: '业务主体不能为空', trigger: 'blur' }],
  businessType: [{ required: true, message: '业务类型不能为空', trigger: 'blur' }],
  currency: [
    { required: true, message: '币种不能为空', trigger: 'blur' },
    {
      pattern: /^[A-Z]{3}$/,
      message: '币种须为3位大写字母（如 CNY）',
      trigger: 'blur',
    },
  ],
  receivableAmount: [
    { required: true, message: '应收金额不能为空' },
    {
      validator: (_: Rule, value: number) => {
        if (value < 0) return Promise.reject('应收金额不能为负数');
        const payable = formData.value.payableAmount ?? 0;
        if (value === 0 && payable === 0)
          return Promise.reject('应收或应付金额至少有一项大于0');
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
  payableAmount: [
    { required: true, message: '应付金额不能为空' },
    {
      validator: (_: Rule, value: number) => {
        if (value < 0) return Promise.reject('应付金额不能为负数');
        const receivable = formData.value.receivableAmount ?? 0;
        if (value === 0 && receivable === 0)
          return Promise.reject('应收或应付金额至少有一项大于0');
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
};

const isEdit = computed(() => !!formData.value?.id);
const isClosed = computed(() => formData.value?.status === 2);
const getTitle = computed(() =>
  isEdit.value
    ? $t('ui.actionTitle.edit', ['业务订单'])
    : $t('ui.actionTitle.create', ['业务订单']),
);

function resetForm() {
  formData.value = {
    id: undefined,
    orderNo: undefined,
    businessSubject: undefined,
    businessType: undefined,
    contractRef: undefined,
    projectRef: undefined,
    receivableAmount: 0,
    payableAmount: 0,
    currency: 'CNY',
    ownerId: undefined,
    status: 0,
    remark: undefined,
  };
  formRef.value?.resetFields();
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (isClosed.value) {
      await modalApi.close();
      return;
    }
    await formRef.value?.validate();
    modalApi.lock();
    const data = formData.value as FinanceBusinessOrderApi.SaveForm;
    try {
      await (data.id ? updateBusinessOrder(data) : createBusinessOrder(data));
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      resetForm();
      return;
    }
    const data = modalApi.getData<{ id?: number }>();
    if (!data?.id) return;
    modalApi.lock();
    try {
      const detail = await getBusinessOrder(data.id);
      formData.value = { ...detail };
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle">
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <Form.Item label="订单编号" name="orderNo">
        <Input
          v-model:value="formData.orderNo"
          placeholder="请输入订单编号"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="业务主体" name="businessSubject">
        <Input
          v-model:value="formData.businessSubject"
          placeholder="请输入业务主体"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="业务类型" name="businessType">
        <Input
          v-model:value="formData.businessType"
          placeholder="请输入业务类型"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="合同编号" name="contractRef">
        <Input
          v-model:value="formData.contractRef"
          placeholder="请输入合同编号（可选）"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="项目编号" name="projectRef">
        <Input
          v-model:value="formData.projectRef"
          placeholder="请输入项目编号（可选）"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="应收金额" name="receivableAmount">
        <InputNumber
          v-model:value="formData.receivableAmount"
          :min="0"
          :precision="2"
          style="width: 100%"
          placeholder="0.00"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="应付金额" name="payableAmount">
        <InputNumber
          v-model:value="formData.payableAmount"
          :min="0"
          :precision="2"
          style="width: 100%"
          placeholder="0.00"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="币种" name="currency">
        <Input
          v-model:value="formData.currency"
          placeholder="如 CNY / USD"
          :maxlength="3"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="状态" name="status">
        <Select
          v-model:value="formData.status"
          :options="ORDER_STATUS_OPTIONS"
          placeholder="请选择状态"
          :disabled="isClosed"
        />
      </Form.Item>
      <Form.Item label="备注" name="remark">
        <Textarea
          v-model:value="formData.remark"
          placeholder="请输入备注"
          :rows="3"
          :disabled="isClosed"
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
