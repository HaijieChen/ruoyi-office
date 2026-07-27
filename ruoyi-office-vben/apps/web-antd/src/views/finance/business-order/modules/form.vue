<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceBusinessOrderApi } from '#/api/finance/business-order';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  message,
  Textarea,
} from 'ant-design-vue';

import {
  createBusinessOrder,
  getBusinessOrder,
  updateBusinessOrder,
} from '#/api/finance/business-order';
import { $t } from '#/locales';

defineOptions({ name: 'FinanceBusinessOrderForm' });

const emit = defineEmits(['success']);

/** Local form state: date fields stored as YYYY-MM-DD strings via value-format on DatePicker. */
interface FormData {
  id?: number;
  bankAccount?: string;
  contractProcessId?: string;
  orderDate?: string;
  productName?: string;
  contactPerson?: string;
  executionStartDate?: string;
  executionEndDate?: string;
  payerName?: string;
  signedExecutionAmount?: number;
  discountRate?: number;
  remark?: string;
  // read-only server fields (displayed only, never sent)
  settlementAmount?: number;
  orderNo?: string;
  importDate?: string;
  importer?: string;
}

const formRef = ref();
const formData = ref<FormData>({});

const rules: Record<string, Rule[]> = {
  bankAccount: [{ required: true, message: '银行账号不能为空', trigger: 'blur' }],
  orderDate: [{ required: true, message: '签单日期不能为空', trigger: 'change' }],
  productName: [{ required: true, message: '产品/服务不能为空', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '联系人不能为空', trigger: 'blur' }],
  executionStartDate: [{ required: true, message: '执行开始日期不能为空', trigger: 'change' }],
  executionEndDate: [{ required: true, message: '执行结束日期不能为空', trigger: 'change' }],
  signedExecutionAmount: [
    { required: true, message: '签约执行金额不能为空' },
    {
      validator: (_: Rule, v: number) =>
        v > 0 ? Promise.resolve() : Promise.reject('签约执行金额必须大于0'),
      trigger: 'change',
    },
  ],
  discountRate: [
    {
      validator: (_: Rule, v: number | undefined) => {
        if (v === undefined || v === null) return Promise.resolve();
        if (v < 0) return Promise.reject('折扣不能为负数');
        const amt = formData.value.signedExecutionAmount ?? 0;
        if (v > amt) return Promise.reject('折扣不能超过签约执行金额');
        return Promise.resolve();
      },
      trigger: 'change',
    },
  ],
};

const isEdit = computed(() => !!formData.value?.id);
const getTitle = computed(() =>
  isEdit.value
    ? $t('ui.actionTitle.edit', ['签单记录'])
    : $t('ui.actionTitle.create', ['签单记录']),
);

function resetForm() {
  formData.value = {};
  formRef.value?.resetFields();
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    const saveData: FinanceBusinessOrderApi.SaveForm = {
      id: formData.value.id,
      bankAccount: formData.value.bankAccount!,
      contractProcessId: formData.value.contractProcessId,
      orderDate: formData.value.orderDate!,
      productName: formData.value.productName!,
      contactPerson: formData.value.contactPerson!,
      executionStartDate: formData.value.executionStartDate!,
      executionEndDate: formData.value.executionEndDate!,
      payerName: formData.value.payerName,
      signedExecutionAmount: formData.value.signedExecutionAmount!,
      discountRate: formData.value.discountRate,
      remark: formData.value.remark,
    };
    try {
      await (saveData.id ? updateBusinessOrder(saveData) : createBusinessOrder(saveData));
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

watch(
  () => formData.value.signedExecutionAmount,
  () => {
    if (formData.value.discountRate !== undefined) {
      formRef.value?.validateFields(['discountRate']);
    }
  },
);
</script>

<template>
  <Modal :title="getTitle" class="w-[640px]">
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 7 }"
      :wrapper-col="{ span: 15 }"
    >
      <template v-if="isEdit">
        <Form.Item label="订单编号">
          <Input :value="formData.orderNo" disabled />
        </Form.Item>
        <Form.Item label="导入日期">
          <Input :value="formData.importDate" disabled />
        </Form.Item>
        <Form.Item label="导入人">
          <Input :value="formData.importer" disabled />
        </Form.Item>
      </template>

      <Form.Item label="银行账号" name="bankAccount">
        <Input v-model:value="formData.bankAccount" placeholder="请输入银行账号" />
      </Form.Item>
      <Form.Item label="合同流程ID" name="contractProcessId">
        <Input v-model:value="formData.contractProcessId" placeholder="可选" />
      </Form.Item>
      <Form.Item label="签单日期" name="orderDate">
        <DatePicker
          v-model:value="formData.orderDate"
          value-format="YYYY-MM-DD"
          placeholder="请选择签单日期"
          style="width: 100%"
        />
      </Form.Item>
      <Form.Item label="产品/服务" name="productName">
        <Input v-model:value="formData.productName" placeholder="请输入产品或服务名称" />
      </Form.Item>
      <Form.Item label="联系人" name="contactPerson">
        <Input v-model:value="formData.contactPerson" placeholder="请输入联系人" />
      </Form.Item>
      <Form.Item label="执行开始日期" name="executionStartDate">
        <DatePicker
          v-model:value="formData.executionStartDate"
          value-format="YYYY-MM-DD"
          placeholder="请选择执行开始日期"
          style="width: 100%"
        />
      </Form.Item>
      <Form.Item label="执行结束日期" name="executionEndDate">
        <DatePicker
          v-model:value="formData.executionEndDate"
          value-format="YYYY-MM-DD"
          placeholder="请选择执行结束日期"
          style="width: 100%"
        />
      </Form.Item>
      <Form.Item label="付款方" name="payerName">
        <Input v-model:value="formData.payerName" placeholder="可选" />
      </Form.Item>
      <Form.Item label="签约执行金额" name="signedExecutionAmount">
        <InputNumber
          v-model:value="formData.signedExecutionAmount"
          :min="0.01"
          :precision="2"
          style="width: 100%"
          placeholder="0.00"
        />
      </Form.Item>
      <Form.Item label="折扣" name="discountRate">
        <InputNumber
          v-model:value="formData.discountRate"
          :min="0"
          :precision="2"
          style="width: 100%"
          placeholder="留空视为0"
        />
      </Form.Item>

      <Form.Item label="结算金额">
        <InputNumber
          v-if="isEdit"
          :value="formData.settlementAmount"
          :precision="2"
          style="width: 100%"
          disabled
        />
        <div v-else class="text-sm text-gray-400 py-1">
          保存后由后端 HALF_UP 精确计算
        </div>
      </Form.Item>

      <Form.Item label="备注" name="remark">
        <Textarea v-model:value="formData.remark" placeholder="可选" :rows="3" />
      </Form.Item>
    </Form>
  </Modal>
</template>
