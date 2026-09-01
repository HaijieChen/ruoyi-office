<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { DatePicker, Form, Select, message } from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import { getCompanyBankAccountPage } from '#/api/finance/company-bank-account';
import {
  getExpenseReimbursement,
  recordPayExpenseReimbursement,
} from '#/api/finance/expense-reimbursement';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinanceExpenseRecordPay' });

const emit = defineEmits(['success']);

const form = ref<{
  id?: number;
  companyBankAccountId?: number;
  actualPayDate?: Dayjs;
  payVoucherUrls: string[];
}>({ payVoucherUrls: [] });

const accountOptions = ref<{ label: string; value: number }[]>([]);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number }>() || {};
    form.value = {
      id: data.id,
      actualPayDate: dayjs(),
      payVoucherUrls: [],
    };
    accountOptions.value = [];
    if (!data.id) return;
    try {
      const bill = await getExpenseReimbursement(data.id);
      const page = await getCompanyBankAccountPage(
        { pageNo: 1, pageSize: 100, status: 0 },
        { hideErrorMessage: true },
      );
      accountOptions.value = (page?.list || []).map((a) => ({
        value: a.id,
        label: `${a.accountName} ${a.accountNoMasked || ''}`,
      }));
      if (bill.companyBankAccountId) {
        form.value.companyBankAccountId = bill.companyBankAccountId;
      }
    } catch {
      // ignore
    }
  },
  async onConfirm() {
    if (!form.value.id || !form.value.companyBankAccountId || !form.value.actualPayDate) {
      message.warning('请填写账户和支付日');
      return;
    }
    modalApi.lock();
    try {
      const urls = form.value.payVoucherUrls.filter(Boolean);
      await recordPayExpenseReimbursement({
        id: form.value.id,
        companyBankAccountId: form.value.companyBankAccountId,
        actualPayDate: form.value.actualPayDate.format('YYYY-MM-DD'),
        payVoucherUrls: urls,
        payVoucherUrl: urls.join(','),
      });
      message.success('已支付');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="支付" class="w-[560px]">
    <Form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <Form.Item label="付款账户" required>
        <Select
          v-model:value="form.companyBankAccountId"
          class="w-full"
          :options="accountOptions"
          show-search
          option-filter-prop="label"
          placeholder="公司银行账户"
        />
      </Form.Item>
      <Form.Item label="支付日期" required>
        <DatePicker v-model:value="form.actualPayDate" class="w-full" />
      </Form.Item>
      <Form.Item label="附件">
        <FileUpload
          :value="form.payVoucherUrls"
          :max-number="30"
          :max-size="20"
          :multiple="true"
          help-text="可选，最多 30 个，不识别金额"
          @update:value="
            (v: string | string[]) => {
              form.payVoucherUrls = (Array.isArray(v) ? v : v ? [v] : []).filter(Boolean);
            }
          "
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
