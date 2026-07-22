<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { FinanceBankReceiptApi } from '#/api/finance/receipt';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Button, message, Table, Upload } from 'ant-design-vue';

import { importBankReceipt, mapFailureRows } from '#/api/finance/receipt';

defineOptions({ name: 'FinanceReceiptImportForm' });

const emit = defineEmits(['success']);

const selectedFile = ref<File | null>(null);
const importing = ref(false);
const result = ref<FinanceBankReceiptApi.ReceiptImportResult | null>(null);

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!selectedFile.value) {
      message.warning('请先选择 Excel 文件');
      return;
    }
    importing.value = true;
    modalApi.lock();
    try {
      result.value = await importBankReceipt(selectedFile.value);
      if (Object.keys(result.value.failureRows).length === 0) {
        message.success(
          `导入成功，共生成 ${result.value.receiptNos.length} 条回单`,
        );
        await modalApi.close();
        emit('success');
      }
      // 有失败行时不关闭弹窗，让用户查看失败详情
    } finally {
      importing.value = false;
      modalApi.unlock();
    }
  },
  onClosed() {
    selectedFile.value = null;
    result.value = null;
  },
});

function beforeUpload(file: FileType) {
  selectedFile.value = file as File;
  result.value = null;
  return false;
}

const failureColumns = [
  { title: '行号', dataIndex: 'rowNum', width: 80 },
  { title: '失败原因', dataIndex: 'reason' },
];
</script>

<template>
  <Modal title="导入银行回单" class="w-2/5">
    <div class="mx-4 space-y-4">
      <Upload
        :before-upload="beforeUpload"
        :max-count="1"
        accept=".xlsx"
        :show-upload-list="!!selectedFile"
      >
        <Button type="primary">选择 .xlsx 文件</Button>
      </Upload>

      <template v-if="result">
        <Alert
          v-if="result.receiptNos.length > 0"
          type="success"
          :message="`成功生成 ${result.receiptNos.length} 条回单：${result.receiptNos.join('、')}`"
          show-icon
        />
        <template v-if="Object.keys(result.failureRows).length > 0">
          <Alert
            type="error"
            :message="`${Object.keys(result.failureRows).length} 行导入失败，请修正后重新导入`"
            show-icon
          />
          <Table
            :data-source="mapFailureRows(result.failureRows)"
            :columns="failureColumns"
            :pagination="false"
            size="small"
            row-key="rowNum"
          />
        </template>
      </template>
    </div>
  </Modal>
</template>
