<script lang="ts" setup>
import { ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, Form, Input, Space, message } from 'ant-design-vue';

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
  /** 附件 URL 列表：上传与粘贴共用唯一数据源 */
  fileUrls: [] as string[],
});

/** 避免 FileUpload 与行内编辑互相顶掉 */
let syncingFromUpload = false;

function normalizeUrls(urls: string[]): string[] {
  return (urls || []).map((u) => String(u ?? '').trim()).filter(Boolean);
}

function addUrlRow() {
  formData.value.fileUrls = [...formData.value.fileUrls, ''];
}

function removeUrlRow(index: number) {
  const next = [...formData.value.fileUrls];
  next.splice(index, 1);
  formData.value.fileUrls = next.length ? next : [];
}

function onFileUploadUpdate(val: string | string[]) {
  syncingFromUpload = true;
  const list = Array.isArray(val) ? val : val ? [val] : [];
  formData.value.fileUrls = list.map((u) => String(u ?? ''));
  queueMicrotask(() => {
    syncingFromUpload = false;
  });
}

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
    const urls = normalizeUrls(formData.value.fileUrls);
    if (!urls.length) {
      message.warning('请至少提供一个发票附件 URL（上传或粘贴）');
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
        files: urls.map((url, idx) => {
          const path = String(url).split('?')[0] || '';
          const base = path.substring(path.lastIndexOf('/') + 1);
          return {
            url,
            name: base || `invoice-${idx + 1}`,
          };
        }),
      });
      message.success('整单办票完成');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

// 若外部清空导致列表为空，保持可编辑体验
watch(
  () => formData.value.fileUrls.length,
  (len) => {
    if (syncingFromUpload) return;
    if (len === 0) {
      // 无行时仍可粘贴：不强制加空行，用户可点「添加」或仅上传
    }
  },
);
</script>

<template>
  <Modal title="整单办票" class="w-[560px]">
    <Form ref="formRef" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <Form.Item label="票号备注">
        <Input
          v-model:value="formData.invoiceNos"
          placeholder="可选，多个票号用逗号分隔（不绑明细行）"
        />
      </Form.Item>
      <Form.Item label="发票附件" required>
        <div class="space-y-2">
          <FileUpload
            :value="formData.fileUrls"
            :max-number="20"
            :max-size="20"
            :multiple="true"
            help-text="可上传；成功后写入下方 URL 列表。再次办票将覆盖整单附件"
            @update:value="onFileUploadUpdate"
          />
          <div class="text-xs text-gray-500">
            或手动填写 / 粘贴 URL（每条可改；至少一条非空）
          </div>
          <div
            v-for="(_url, index) in formData.fileUrls"
            :key="index"
            class="flex items-center gap-2"
          >
            <Input
              v-model:value="formData.fileUrls[index]"
              placeholder="https://… 或上传回填的地址"
              allow-clear
            />
            <Button type="link" danger @click="removeUrlRow(index)">删除</Button>
          </div>
          <Space>
            <Button size="small" @click="addUrlRow">添加 URL</Button>
          </Space>
        </div>
      </Form.Item>
    </Form>
  </Modal>
</template>
