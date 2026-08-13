<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { DatePicker, Form, Input, InputNumber, Select, message } from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import {
  getPaymentApplication,
  recordPayPaymentApplication,
} from '#/api/finance/payment-application';
import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinancePaymentRecordPay' });

const emit = defineEmits(['success']);

const form = ref<{
  id?: number;
  taskId?: string;
  companyBankAccountId?: number;
  payAmount?: number;
  actualPayDate?: Dayjs;
  payVoucherUrl?: string;
  erpVoucherNo?: string;
  entityCompanyDeptId?: number;
  applyAmount?: number;
  paidLineSum?: number;
}>({});

const accountOptions = ref<
  { label: string; value: number; currency?: string }[]
>([]);

function onVoucherUpload(val: string | string[]) {
  const arr = Array.isArray(val) ? val : val ? [val] : [];
  form.value.payVoucherUrl = arr[0] || '';
}

async function loadAccounts(entityCompanyDeptId?: number) {
  accountOptions.value = [];
  if (!entityCompanyDeptId) return;
  try {
    const list = await getCompanyBankAccountSimpleList(entityCompanyDeptId);
    accountOptions.value = (list || []).map((a) => ({
      label: `${a.accountName} / ${a.bankName} / ${a.accountNoMasked || ''}`,
      value: a.id,
      currency: a.currency,
    }));
  } catch {
    accountOptions.value = [];
  }
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number; taskId?: string }>() || {};
    form.value = {
      id: data.id,
      taskId: data.taskId || '',
      actualPayDate: dayjs(),
    };
    if (data.id) {
      try {
        const app = await getPaymentApplication(data.id);
        form.value.entityCompanyDeptId = app.entityCompanyDeptId;
        form.value.applyAmount = Number(app.applyAmount || 0);
        form.value.paidLineSum = Number((app as any).paidLineSum || 0);
        const remain =
          form.value.applyAmount - (form.value.paidLineSum || 0);
        form.value.payAmount = remain > 0 ? remain : form.value.applyAmount;
        await loadAccounts(app.entityCompanyDeptId);
      } catch {
        // ignore; 用户仍可手填 task
      }
    }
  },
  async onConfirm() {
    if (!form.value.id || !form.value.taskId) {
      message.error('缺少申请 id 或任务 id（待办入口请带 taskId）');
      return;
    }
    if (!form.value.companyBankAccountId) {
      message.error('请选择付款账户');
      return;
    }
    if (!form.value.actualPayDate || !form.value.payVoucherUrl) {
      message.error('支付日与支付凭证必填');
      return;
    }
    modalApi.lock();
    try {
      await recordPayPaymentApplication({
        id: form.value.id,
        taskId: form.value.taskId,
        companyBankAccountId: form.value.companyBankAccountId,
        payAmount: form.value.payAmount,
        actualPayDate: form.value.actualPayDate.format('YYYY-MM-DD'),
        payVoucherUrl: form.value.payVoucherUrl,
        erpVoucherNo: form.value.erpVoucherNo,
      });
      message.success('出纳支付已登记');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

watch(
  () => form.value.entityCompanyDeptId,
  (v) => loadAccounts(v),
);

onMounted(() => {});
</script>

<template>
  <Modal title="出纳支付办结" class="w-[560px]">
    <Form :label-col="{ span: 7 }" :wrapper-col="{ span: 15 }">
      <Form.Item label="任务 ID" required>
        <Input
          v-model:value="form.taskId"
          placeholder="BPM 待办 taskId"
        />
      </Form.Item>
      <Form.Item label="付款账户" required>
        <Select
          v-model:value="form.companyBankAccountId"
          :options="accountOptions"
          show-search
          option-filter-prop="label"
          placeholder="仅显示该主体启用账户（账号脱敏）"
        />
      </Form.Item>
      <Form.Item label="本笔金额" required>
        <InputNumber
          v-model:value="form.payAmount"
          class="w-full"
          :min="0.01"
          :precision="2"
        />
      </Form.Item>
      <Form.Item label="实际支付日期" required>
        <DatePicker v-model:value="form.actualPayDate" class="w-full" />
      </Form.Item>
      <Form.Item label="支付凭证" required>
        <FileUpload
          :value="form.payVoucherUrl ? [form.payVoucherUrl] : []"
          :max-number="1"
          :max-size="20"
          :multiple="false"
          help-text="上传回单/截图"
          @update:value="onVoucherUpload"
        />
      </Form.Item>
      <Form.Item label="ERP 凭证号">
        <Input v-model:value="form.erpVoucherNo" />
      </Form.Item>
    </Form>
  </Modal>
</template>
