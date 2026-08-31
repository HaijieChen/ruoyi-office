<script lang="ts" setup>
import type { FinanceInvoiceApplicationApi } from '#/api/finance/invoice-application';

import { ref } from 'vue';

import { Button, Modal, Table, message } from 'ant-design-vue';

import { displayDateTime } from '#/utils/display-time';
import {
  downloadAuthFile,
  fetchPreviewBlob,
  guessPreviewKind,
} from '#/utils/file-preview';

defineOptions({ name: 'FinanceInvoiceIssueFilesTable' });

defineProps<{
  files: FinanceInvoiceApplicationApi.IssueFile[];
}>();

const previewOpen = ref(false);
const previewTitle = ref('预览');
const previewSrc = ref('');
const previewKind = ref<'image' | 'pdf'>('image');
let previewObjectUrl = '';

function closePreview() {
  previewOpen.value = false;
  previewSrc.value = '';
  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl);
    previewObjectUrl = '';
  }
}

async function previewFile(file: FinanceInvoiceApplicationApi.IssueFile) {
  const url = file.fileUrl;
  if (!url) {
    message.warning('没有可预览的地址');
    return;
  }
  const nameOrUrl = file.fileName || url;
  const kind = guessPreviewKind(nameOrUrl) || guessPreviewKind(url);
  if (!kind) {
    message.info('该文件类型不支持在线预览，请下载查看');
    return;
  }
  try {
    const blob = await fetchPreviewBlob(url);
    const obj = URL.createObjectURL(blob);
    previewObjectUrl = obj;
    previewSrc.value = obj;
    previewKind.value = kind;
    previewTitle.value = file.fileName || '预览';
    previewOpen.value = true;
  } catch (e: any) {
    message.error(e?.message || '无法在本页预览该文件');
  }
}

async function downloadFile(file: FinanceInvoiceApplicationApi.IssueFile) {
  const url = file.fileUrl;
  if (!url) {
    message.warning('没有可下载的地址');
    return;
  }
  try {
    await downloadAuthFile(url, file.fileName);
  } catch (e: any) {
    message.error(e?.message || '下载失败');
  }
}

const columns = [
  {
    title: '附件',
    dataIndex: 'fileName',
    key: 'fileName',
    ellipsis: true,
    customRender: ({
      record,
    }: {
      record: FinanceInvoiceApplicationApi.IssueFile;
    }) => record.fileName || record.fileUrl || '-',
  },
  {
    title: '金额',
    dataIndex: 'amount',
    key: 'amount',
    width: 110,
    customRender: ({ text }: { text?: number }) =>
      text != null ? `¥${Number(text).toFixed(2)}` : '-',
  },
  {
    title: '发票号',
    dataIndex: 'invoiceNo',
    key: 'invoiceNo',
    width: 140,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    title: '开票日期',
    dataIndex: 'invoiceDate',
    key: 'invoiceDate',
    width: 120,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    title: '上传时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 160,
    customRender: ({ text }: { text?: string }) => displayDateTime(text) || '-',
  },
  {
    title: '操作',
    key: 'actions',
    width: 140,
  },
];
</script>

<template>
  <Table
    size="small"
    :columns="columns"
    :data-source="files"
    :pagination="false"
    row-key="id"
    bordered
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'actions'">
        <template v-if="record.fileUrl">
          <Button type="link" size="small" class="px-1" @click="previewFile(record)">
            预览
          </Button>
          <Button type="link" size="small" class="px-1" @click="downloadFile(record)">
            下载
          </Button>
        </template>
        <span v-else>-</span>
      </template>
    </template>
  </Table>
  <Modal
    :open="previewOpen"
    :title="previewTitle"
    :footer="null"
    width="720px"
    destroy-on-close
    @cancel="closePreview"
  >
    <img
      v-if="previewKind === 'image'"
      :src="previewSrc"
      :alt="previewTitle"
      class="max-h-[70vh] w-full object-contain"
    />
    <iframe
      v-else
      :src="previewSrc"
      class="h-[70vh] w-full border-0"
      title="pdf-preview"
    />
  </Modal>
</template>
