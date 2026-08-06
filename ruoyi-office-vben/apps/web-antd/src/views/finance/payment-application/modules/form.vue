<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';
import type { DefaultOptionType, SelectValue } from 'ant-design-vue/es/select';

import type { FinancePaymentApplicationApi } from '#/api/finance/payment-application';

import { computed, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Form,
  Input,
  InputNumber,
  Select,
  message,
} from 'ant-design-vue';

import { getDictOptions } from '@vben/hooks';

import {
  createAndStartPaymentApplication,
  getCumulativePaid,
  getPaymentApplication,
  listSelectableLeaseContracts,
  listSelectablePurchaseInstances,
  resubmitPaymentApplication,
} from '#/api/finance/payment-application';
import { getCustomerCompanySimpleList } from '#/api/finance/customer-company';
import { listSelectableContractsForBo } from '#/api/finance/contract-application';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinancePaymentApplicationForm' });

const emit = defineEmits(['success']);

interface FormData {
  id?: number;
  paymentTiming?: string;
  paymentReason?: string;
  purchaseProcessInstanceId?: string;
  leaseContractApplicationId?: number;
  relatedContractApplicationId?: number;
  payeeCompanyId?: number;
  payeeBankName?: string;
  payeeBankAccount?: string;
  applyAmount?: number;
  currency?: string;
  businessSettlementTerm?: string;
  payMethod?: string;
  costProject?: string;
  accountingSubject?: string;
  /** 上传组件用 string[]；提交时直接作为 evidenceFileUrls */
  evidenceFileUrls?: string[];
  specialNote?: string;
}

const formRef = ref();
const formData = ref<FormData>({
  currency: 'CNY',
  paymentTiming: 'IMMEDIATE',
});
const mode = ref<'create' | 'resubmit'>('create');
const supplierOptions = ref<{ label: string; value: number; bankName?: string; bankAccount?: string }[]>([]);
const purchaseOptions = ref<{ label: string; value: string }[]>([]);
const leaseOptions = ref<{ label: string; value: number }[]>([]);
const relatedContractOptions = ref<{ label: string; value: number }[]>([]);
const cumulativePaid = ref(0);

const isPurchase = computed(() => formData.value.paymentReason === 'PURCHASE');
const isLease = computed(() => formData.value.paymentReason === 'LEASE');
const isBusiness = computed(() => formData.value.paymentReason === 'BUSINESS');
const cumulativeAfter = computed(() => {
  const amt = Number(formData.value.applyAmount || 0);
  return Number(cumulativePaid.value) + amt;
});

const rules: Record<string, Rule[]> = {
  paymentTiming: [{ required: true, message: '请选择支付时效' }],
  paymentReason: [{ required: true, message: '请选择付款事由' }],
  payeeCompanyId: [{ required: true, message: '请选择收款方' }],
  applyAmount: [{ required: true, message: '请输入金额' }],
  businessSettlementTerm: [{ required: true, message: '请填写账期' }],
  payMethod: [{ required: true, message: '请选择支付方式' }],
  costProject: [{ required: true, message: '请选择费用项目' }],
  evidenceFileUrls: [
    {
      required: true,
      validator: async (_rule, value: string[] | undefined) => {
        if (!value || !value.some((u) => String(u || '').trim())) {
          throw new Error('请上传至少一份付款依据');
        }
      },
    },
  ],
};

const title = computed(() =>
  mode.value === 'resubmit' ? '重提付款申请' : '发起付款申请',
);

async function loadSuppliers() {
  const list = (await getCustomerCompanySimpleList('SUPPLIER')) || [];
  supplierOptions.value = list.map((c) => ({
    label: `${c.name}（${c.taxNo || '-'}）`,
    value: c.id,
    bankName: c.bankName,
    bankAccount: c.bankAccount,
  }));
}

async function loadPurchase() {
  const list = (await listSelectablePurchaseInstances()) || [];
  purchaseOptions.value = list.map((p) => ({
    label: p.summary || p.name || p.processInstanceId,
    value: p.processInstanceId,
  }));
}

async function loadLease() {
  const list = (await listSelectableLeaseContracts()) || [];
  leaseOptions.value = (list as any[]).map((c) => ({
    label: `${c.applicationNo || c.id} ${c.fileName || c.counterpartyName || ''}`,
    value: c.id,
  }));
}

async function loadRelatedContracts() {
  const list = (await listSelectableContractsForBo()) || [];
  relatedContractOptions.value = list.map((c) => ({
    label: `${c.applicationNo || c.id} ${c.fileName || c.counterpartyName || ''}`,
    value: c.id,
  }));
}

function onEvidenceUpload(val: string | string[]) {
  const urls = Array.isArray(val) ? val : val ? [val] : [];
  formData.value.evidenceFileUrls = urls.filter(Boolean);
}

/** ant-design-vue Select options 与 DictDataType 值域不完全兼容，映射为 DefaultOptionType */
function dictOptions(dictType: string): DefaultOptionType[] {
  return getDictOptions(dictType).map((d) => ({
    label: d.label,
    value: d.value as string | number,
  }));
}

function onPayeeChange(value: SelectValue) {
  const id =
    typeof value === 'number'
      ? value
      : value != null && value !== ''
        ? Number(value)
        : NaN;
  if (!Number.isFinite(id)) {
    return;
  }
  const opt = supplierOptions.value.find((o) => o.value === id);
  if (opt) {
    formData.value.payeeBankName = opt.bankName;
    formData.value.payeeBankAccount = opt.bankAccount;
  }
  void refreshCumulative(id);
}

async function refreshCumulative(payeeId?: number) {
  if (!payeeId) {
    cumulativePaid.value = 0;
    return;
  }
  try {
    const res = await getCumulativePaid(payeeId);
    cumulativePaid.value = Number(res?.paidSum ?? 0);
  } catch {
    cumulativePaid.value = 0;
  }
}

watch(
  () => formData.value.paymentReason,
  async (r) => {
    if (r === 'PURCHASE') await loadPurchase();
    if (r === 'LEASE') await loadLease();
    if (r === 'BUSINESS') await loadRelatedContracts();
    if (r !== 'BUSINESS') {
      formData.value.relatedContractApplicationId = undefined;
    }
  },
);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    await loadSuppliers();
    const data = modalApi.getData<{ id?: number; mode?: string }>() || {};
    mode.value = data.mode === 'resubmit' ? 'resubmit' : 'create';
    if (data.id) {
      const detail = await getPaymentApplication(data.id);
      formData.value = {
        id: detail.id,
        paymentTiming: detail.paymentTiming,
        paymentReason: detail.paymentReason,
        purchaseProcessInstanceId: detail.purchaseProcessInstanceId,
        leaseContractApplicationId: detail.leaseContractApplicationId,
        relatedContractApplicationId: detail.relatedContractApplicationId,
        payeeCompanyId: detail.payeeCompanyId,
        payeeBankName: detail.payeeBankName,
        payeeBankAccount: detail.payeeBankAccount,
        applyAmount: detail.applyAmount,
        currency: detail.currency || 'CNY',
        businessSettlementTerm: detail.businessSettlementTerm,
        payMethod: detail.payMethod,
        costProject: detail.costProject,
        accountingSubject: detail.accountingSubject,
        evidenceFileUrls: (() => {
          try {
            const arr = JSON.parse(detail.evidenceFileUrls || '[]');
            return Array.isArray(arr) ? arr.filter(Boolean) : [];
          } catch {
            const s = detail.evidenceFileUrls || '';
            return s
              ? s
                  .split(/[,，\n]/)
                  .map((x) => x.trim())
                  .filter(Boolean)
              : [];
          }
        })(),
        specialNote: detail.specialNote,
      };
      await refreshCumulative(detail.payeeCompanyId);
      if (detail.paymentReason === 'PURCHASE') await loadPurchase();
      if (detail.paymentReason === 'LEASE') await loadLease();
      if (detail.paymentReason === 'BUSINESS') await loadRelatedContracts();
    } else {
      formData.value = {
        currency: 'CNY',
        paymentTiming: 'IMMEDIATE',
        evidenceFileUrls: [],
      };
      cumulativePaid.value = 0;
    }
  },
  async onConfirm() {
    if (isPurchase.value && !formData.value.purchaseProcessInstanceId) {
      message.error('采购付款须选择已通过的采购实例');
      return;
    }
    if (isLease.value && !formData.value.leaseContractApplicationId) {
      message.error('房屋租赁须选择已通过的租赁合同');
      return;
    }
    await formRef.value?.validate();
    modalApi.lock();
    try {
      const urls = (formData.value.evidenceFileUrls || [])
        .map((s) => String(s).trim())
        .filter(Boolean);
      const payload: FinancePaymentApplicationApi.CreateAndStartRequest = {
        paymentTiming: formData.value.paymentTiming!,
        paymentReason: formData.value.paymentReason!,
        purchaseProcessInstanceId: isPurchase.value
          ? formData.value.purchaseProcessInstanceId
          : undefined,
        leaseContractApplicationId: isLease.value
          ? formData.value.leaseContractApplicationId
          : undefined,
        relatedContractApplicationId: isBusiness.value
          ? formData.value.relatedContractApplicationId
          : undefined,
        payeeCompanyId: formData.value.payeeCompanyId!,
        payeeBankName: formData.value.payeeBankName,
        payeeBankAccount: formData.value.payeeBankAccount,
        applyAmount: formData.value.applyAmount!,
        currency: formData.value.currency || 'CNY',
        businessSettlementTerm: formData.value.businessSettlementTerm!,
        payMethod: formData.value.payMethod!,
        costProject: formData.value.costProject!,
        accountingSubject: formData.value.accountingSubject,
        evidenceFileUrls: urls,
        specialNote: formData.value.specialNote,
      };
      if (mode.value === 'resubmit' && formData.value.id) {
        await resubmitPaymentApplication(formData.value.id, payload);
        message.success('重提成功');
      } else {
        await createAndStartPaymentApplication(payload);
        message.success('提交成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="title" class="w-[720px]">
    <div class="mb-2 text-sm text-gray-500">
      支付时效仅一处；收款账户带出后可改本次，不回写客商档案。累计 = 已支付
      {{ cumulativePaid }} + 本次 → {{ cumulativeAfter }}
    </div>
    <Form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <Form.Item label="支付时效" name="paymentTiming" required>
        <Select
          v-model:value="formData.paymentTiming"
          :options="dictOptions('finance_payment_timing')"
          placeholder="即时/月底/通知"
        />
      </Form.Item>
      <Form.Item label="付款事由" name="paymentReason" required>
        <Select
          v-model:value="formData.paymentReason"
          :options="dictOptions('finance_payment_reason')"
        />
      </Form.Item>
      <Form.Item v-if="isPurchase" label="采购实例" required>
        <Select
          v-model:value="formData.purchaseProcessInstanceId"
          :options="purchaseOptions"
          show-search
          allow-clear
          placeholder="已通过采购流程"
        />
      </Form.Item>
      <Form.Item v-if="isLease" label="租赁合同" required>
        <Select
          v-model:value="formData.leaseContractApplicationId"
          :options="leaseOptions"
          show-search
          allow-clear
        />
      </Form.Item>
      <Form.Item v-if="isBusiness" label="关联合同（可选）">
        <Select
          v-model:value="formData.relatedContractApplicationId"
          :options="relatedContractOptions"
          show-search
          allow-clear
          placeholder="本人已通过合同，带出结算方式"
        />
      </Form.Item>
      <Form.Item label="收款方" name="payeeCompanyId" required>
        <Select
          v-model:value="formData.payeeCompanyId"
          :options="supplierOptions"
          show-search
          @change="onPayeeChange"
        />
      </Form.Item>
      <Form.Item label="开户行">
        <Input v-model:value="formData.payeeBankName" />
      </Form.Item>
      <Form.Item label="银行账号">
        <Input v-model:value="formData.payeeBankAccount" />
      </Form.Item>
      <Form.Item label="申请金额" name="applyAmount" required>
        <InputNumber
          v-model:value="formData.applyAmount"
          :min="0.01"
          class="w-full"
        />
      </Form.Item>
      <Form.Item label="账期" name="businessSettlementTerm" required>
        <Input v-model:value="formData.businessSettlementTerm" />
      </Form.Item>
      <Form.Item label="支付方式" name="payMethod" required>
        <Select
          v-model:value="formData.payMethod"
          :options="dictOptions('finance_pay_method')"
        />
      </Form.Item>
      <Form.Item label="费用项目" name="costProject" required>
        <Select
          v-model:value="formData.costProject"
          :options="dictOptions('finance_cost_project')"
        />
      </Form.Item>
      <Form.Item label="付款依据" name="evidenceFileUrls" required>
        <FileUpload
          :value="formData.evidenceFileUrls"
          :max-number="10"
          :max-size="20"
          :multiple="true"
          help-text="上传回单/合同等依据，至少一份"
          @update:value="onEvidenceUpload"
        />
      </Form.Item>
      <Form.Item label="特殊说明">
        <Input.TextArea v-model:value="formData.specialNote" :rows="2" />
      </Form.Item>
    </Form>
  </Modal>
</template>
