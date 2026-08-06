<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { DatePicker, Form, Input, message } from 'ant-design-vue';
import dayjs, { type Dayjs } from 'dayjs';

import { recordPayPaymentApplication } from '#/api/finance/payment-application';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinancePaymentRecordPay' });

const emit = defineEmits(['success']);

const form = ref<{
  id?: number;
  taskId?: string;
  actualPayDate?: Dayjs;
  payVoucherUrl?: string;
  erpVoucherNo?: string;
}>({});

function onVoucherUpload(val: string | string[]) {
  const arr = Array.isArray(val) ? val : val ? [val] : [];
  form.value.payVoucherUrl = arr[0] || '';
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ id?: number; taskId?: string }>() || {};
    form.value = {
      id: data.id,
      taskId: data.taskId || '',
      actualPayDate: dayjs(),
    };
  },
  async onConfirm() {
    if (!form.value.id || !form.value.taskId) {
      message.error('缺少申请 id 或任务 id（待办入口请带 taskId）');
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
        actualPayDate: form.value.actualPayDate.format('YYYY-MM-DD'),
        payVoucherUrl: form.value.payVoucherUrl,
        erpVoucherNo: form.value.erpVoucherNo,
      });
      message.success('出纳办结已提交');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="出纳支付办结" class="w-[520px]">
    <Form :label-col="{ span: 7 }" :wrapper-col="{ span: 15 }">
      <Form.Item label="任务 ID" required>
        <Input
          v-model:value="form.taskId"
          placeholder="BPM 待办 taskId"
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
