<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Form, Input, message } from 'ant-design-vue';

import {
  completeInvoiceIssue,
  getInvoiceApplication,
} from '#/api/finance/invoice-application';
import { FileUpload } from '#/components/upload';

defineOptions({ name: 'FinanceInvoiceIssueForm' });

const emit = defineEmits(['success']);

const formRef = ref();
const formData = ref({
  applicationId: undefined as number | undefined,
  invoiceNos: '',
  /** FileUpload 多附件 URL 列表 */
  fileUrls: [] as string[],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<{ applicationId: number }>() || {};
    formData.value = {
      applicationId: data.applicationId,
      invoiceNos: '',
      fileUrls: [],
    };
    if (data.applicationId) {
      try {
        const detail = await getInvoiceApplication(data.applicationId);
        const files = detail.files || [];
        formData.value.fileUrls = files
          .map((f) => f.fileUrl)
          .filter((u): u is string => !!u);
        // 兼容旧行上票号展示
        const lineNos = (detail.lines || [])
          .map((l) => l.invoiceNo)
          .filter(Boolean);
        if (lineNos.length) {
          formData.value.invoiceNos = lineNos.join(',');
        }
      } catch {
        // 详情失败仍可提交新附件
      }
    }
  },
  async onConfirm() {
    if (!formData.value.applicationId) {
      message.warning('缺少开票申请编号');
      return;
    }
    const urls = (formData.value.fileUrls || []).filter(Boolean);
    if (!urls.length) {
      message.warning('请至少上传一个发票附件');
      return;
    }
    modalApi.lock();
    try {
      const invoiceNos = formData.value.invoiceNos
        ? formData.value.invoiceNos
            .split(/[,，\s]+/)
            .map((s) => s.trim())
            .filter(Boolean)
        : undefined;
      await completeInvoiceIssue({
        applicationId: formData.value.applicationId,
        invoiceNos,
        files: urls.map((url, idx) => ({
          url,
          name: `invoice-${idx + 1}`,
        })),
      });
      message.success('整单办票完成');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="整单办票" class="w-[560px]">
    <Form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <Form.Item label="票号备注">
        <Input
          v-model:value="formData.invoiceNos"
          placeholder="可选，多个票号用逗号分隔（不绑明细行）"
        />
      </Form.Item>
      <Form.Item label="发票附件" required>
        <FileUpload
          v-model:value="formData.fileUrls"
          :max-number="20"
          :max-size="20"
          :multiple="true"
          help-text="支持多附件；再次办票将覆盖整单附件列表"
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
