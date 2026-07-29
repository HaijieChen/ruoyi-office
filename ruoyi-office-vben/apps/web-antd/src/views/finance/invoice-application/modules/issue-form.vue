<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Form, Input, Select, message } from 'ant-design-vue';

import {
  getInvoiceApplication,
  updateInvoiceIssueProgress,
} from '#/api/finance/invoice-application';

defineOptions({ name: 'FinanceInvoiceIssueForm' });

const emit = defineEmits(['success']);

const formRef = ref();
const formData = ref({
  applicationId: undefined as number | undefined,
  lineId: undefined as number | undefined,
  invoiceNo: '',
  fileUrl: '',
});
const lineOptions = ref<Array<{ label: string; value: number }>>([]);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ applicationId: number }>() || {};
    formData.value = {
      applicationId: data.applicationId,
      lineId: undefined,
      invoiceNo: '',
      fileUrl: '',
    };
    if (data.applicationId) {
      const detail = await getInvoiceApplication(data.applicationId);
      lineOptions.value = (detail.lines || [])
        .filter((l) => !l.issueStatus || l.issueStatus === 0)
        .map((l) => ({
          value: l.id as number,
          label: `行#${l.id} BO=${l.businessOrderId} 金额=${l.amount}`,
        }));
    }
  },
  async onConfirm() {
    if (!formData.value.applicationId || !formData.value.lineId) {
      message.warning('请选择明细行');
      return;
    }
    if (!formData.value.invoiceNo) {
      message.warning('请填写票号');
      return;
    }
    modalApi.lock();
    try {
      await updateInvoiceIssueProgress({
        applicationId: formData.value.applicationId,
        lineId: formData.value.lineId,
        invoiceNo: formData.value.invoiceNo,
        fileUrl: formData.value.fileUrl || undefined,
      });
      message.success('办票进度已更新（一行一票）');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="办票（一行一票）" class="w-[520px]">
    <Form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <Form.Item label="明细行" required>
        <Select
          v-model:value="formData.lineId"
          :options="lineOptions"
          placeholder="选择未开票明细"
          class="w-full"
        />
      </Form.Item>
      <Form.Item label="票号" required>
        <Input v-model:value="formData.invoiceNo" />
      </Form.Item>
      <Form.Item label="附件 URL">
        <Input v-model:value="formData.fileUrl" />
      </Form.Item>
    </Form>
  </Modal>
</template>
