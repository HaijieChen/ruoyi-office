<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { FinanceBankReceiptApi } from '#/api/finance/receipt';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Textarea,
  message,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  createReceipt,
  getReceipt,
  updateReceipt,
} from '#/api/finance/receipt';
import { getSimpleCompanyList } from '#/api/system/dept';

defineOptions({ name: 'FinanceReceiptForm' });

const emit = defineEmits(['success']);

interface CompanyOption {
  label: string;
  value: number;
}

interface FormData {
  id?: number;
  entityCompanyDeptId?: number;
  entityCompanyName?: string;
  bankAccount?: string;
  /** DatePicker 绑定字符串；提交时转 epoch millis */
  transactionDate?: string;
  payerName?: string;
  payerAccount?: string;
  transactionAmount?: number;
  summary?: string;
  bankSerialNo?: string;
  /** 是否业务款，新增默认 true */
  businessFund?: boolean;
  fundTypeRemark?: string;
  receiptNo?: string;
}

function defaultFormData(): FormData {
  return { businessFund: true };
}

function toDisplayDateTime(value: unknown): string | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  if (typeof value === 'number') {
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  }
  if (typeof value === 'string') {
    const n = Number(value);
    if (!Number.isNaN(n) && n > 1e11) {
      return dayjs(n).format('YYYY-MM-DD HH:mm:ss');
    }
    return dayjs(value).isValid()
      ? dayjs(value).format('YYYY-MM-DD HH:mm:ss')
      : undefined;
  }
  if (Array.isArray(value) && value.length >= 5) {
    const [y, m, d, h = 0, mi = 0, s = 0] = value as number[];
    return dayjs(new Date(y, m - 1, d, h, mi, s)).format('YYYY-MM-DD HH:mm:ss');
  }
  return undefined;
}

function toEpochMillis(value?: string): number | undefined {
  if (!value) {
    return undefined;
  }
  const d = dayjs(value);
  return d.isValid() ? d.valueOf() : undefined;
}

const formRef = ref();
const formData = ref<FormData>({});
const companyOptions = ref<CompanyOption[]>([]);
const loadingCompany = ref(false);

const isEdit = computed(() => !!formData.value.id);
const getTitle = computed(() =>
  isEdit.value ? '编辑银行到款' : '新增银行到款',
);

const rules: Record<string, Rule[]> = {
  entityCompanyDeptId: [
    { required: true, message: '主体公司不能为空', trigger: 'change' },
  ],
  bankAccount: [{ required: true, message: '银行账户不能为空', trigger: 'blur' }],
  transactionDate: [
    { required: true, message: '交易日期不能为空', trigger: 'change' },
  ],
  payerName: [{ required: true, message: '付款方名称不能为空', trigger: 'blur' }],
  transactionAmount: [
    { required: true, message: '交易金额不能为空' },
    {
      validator: (_: Rule, v: number) =>
        v > 0 ? Promise.resolve() : Promise.reject('交易金额必须大于 0'),
      trigger: 'change',
    },
  ],
  bankSerialNo: [
    { required: true, message: '银行流水号不能为空', trigger: 'blur' },
  ],
  businessFund: [
    { required: true, message: '是否业务款不能为空', trigger: 'change' },
  ],
};

function resetForm() {
  formData.value = defaultFormData();
  formRef.value?.resetFields();
}

async function loadCompanyOptions() {
  loadingCompany.value = true;
  try {
    const list = (await getSimpleCompanyList()) || [];
    companyOptions.value = list
      .filter((c) => c.id != null)
      .map((c) => ({
        value: c.id as number,
        label: c.name,
      }));
  } finally {
    loadingCompany.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      companyOptions.value = [];
      return;
    }
    await loadCompanyOptions();
    const data = modalApi.getData<{ id?: number }>() || {};
    if (data.id) {
      const detail = await getReceipt(data.id);
      formData.value = {
        id: detail.id,
        entityCompanyDeptId: detail.entityCompanyDeptId,
        entityCompanyName: detail.entityCompanyName,
        bankAccount: detail.bankAccount,
        transactionDate: toDisplayDateTime(detail.transactionDate),
        payerName: detail.payerName,
        payerAccount: detail.payerAccount,
        transactionAmount: detail.transactionAmount,
        summary: detail.summary,
        bankSerialNo: detail.bankSerialNo,
        businessFund: detail.businessFund ?? true,
        fundTypeRemark: detail.fundTypeRemark,
        receiptNo: detail.receiptNo,
      };
      if (
        detail.entityCompanyDeptId != null &&
        !companyOptions.value.some((o) => o.value === detail.entityCompanyDeptId)
      ) {
        companyOptions.value = [
          {
            value: detail.entityCompanyDeptId,
            label:
              detail.entityCompanyName ||
              `公司 #${detail.entityCompanyDeptId}`,
          },
          ...companyOptions.value,
        ];
      }
    } else {
      formData.value = defaultFormData();
    }
  },
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const transactionDate = toEpochMillis(formData.value.transactionDate);
      if (transactionDate == null) {
        message.error('交易日期无效');
        return;
      }
      const payload: FinanceBankReceiptApi.SaveForm = {
        id: formData.value.id,
        entityCompanyDeptId: formData.value.entityCompanyDeptId!,
        bankAccount: formData.value.bankAccount!,
        // 后端 LocalDateTime 使用 epoch millis 反序列化
        transactionDate: transactionDate as unknown as string,
        payerName: formData.value.payerName!,
        payerAccount: formData.value.payerAccount,
        transactionAmount: formData.value.transactionAmount!,
        summary: formData.value.summary,
        bankSerialNo: formData.value.bankSerialNo!,
        businessFund: formData.value.businessFund ?? true,
        fundTypeRemark: formData.value.fundTypeRemark,
      };
      if (payload.id) {
        await updateReceipt(payload);
        message.success('更新成功');
      } else {
        await createReceipt(payload);
        message.success('创建成功');
      }
      emit('success');
      await modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  onClosed() {
    resetForm();
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[640px]">
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 5 }"
      :wrapper-col="{ span: 18 }"
      class="px-2"
    >
      <Form.Item v-if="formData.receiptNo" label="到款流水号">
        <Input :value="formData.receiptNo" disabled />
      </Form.Item>
      <Form.Item label="主体公司" name="entityCompanyDeptId">
        <Select
          v-model:value="formData.entityCompanyDeptId"
          class="w-full"
          show-search
          allow-clear
          :loading="loadingCompany"
          :options="companyOptions"
          option-filter-prop="label"
          placeholder="请选择组织架构中的公司"
          @dropdown-visible-change="
            (open: boolean) => {
              if (open && !companyOptions.length) loadCompanyOptions();
            }
          "
        />
      </Form.Item>
      <Form.Item label="银行账户" name="bankAccount">
        <Input
          v-model:value="formData.bankAccount"
          placeholder="请输入银行账户"
          allow-clear
        />
      </Form.Item>
      <Form.Item label="交易日期" name="transactionDate">
        <DatePicker
          v-model:value="formData.transactionDate"
          show-time
          value-format="YYYY-MM-DD HH:mm:ss"
          class="w-full"
          placeholder="请选择交易日期"
        />
      </Form.Item>
      <Form.Item label="付款方名称" name="payerName">
        <Input
          v-model:value="formData.payerName"
          placeholder="请输入付款方名称"
          allow-clear
        />
      </Form.Item>
      <Form.Item label="付款方账号" name="payerAccount">
        <Input
          v-model:value="formData.payerAccount"
          placeholder="可选"
          allow-clear
        />
      </Form.Item>
      <Form.Item label="交易金额" name="transactionAmount">
        <InputNumber
          v-model:value="formData.transactionAmount"
          :min="0.01"
          :precision="2"
          class="w-full"
          placeholder="请输入交易金额"
        />
      </Form.Item>
      <Form.Item label="银行流水号" name="bankSerialNo">
        <Input
          v-model:value="formData.bankSerialNo"
          placeholder="唯一，不可重复"
          allow-clear
        />
      </Form.Item>
      <Form.Item label="是否业务款" name="businessFund">
        <Radio.Group v-model:value="formData.businessFund">
          <Radio :value="true">是</Radio>
          <Radio :value="false">否</Radio>
        </Radio.Group>
      </Form.Item>
      <Form.Item label="款项类型备注" name="fundTypeRemark">
        <Input
          v-model:value="formData.fundTypeRemark"
          placeholder="可选，最长 255 字"
          :maxlength="255"
          allow-clear
          show-count
        />
      </Form.Item>
      <Form.Item label="摘要/附言" name="summary">
        <Textarea
          v-model:value="formData.summary"
          :rows="2"
          placeholder="可选"
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
