<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { FinanceBusinessOrderApi } from '#/api/finance/business-order';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Button, Input, message, Table, Upload } from 'ant-design-vue';

import { importBusinessOrder } from '#/api/finance/business-order';

defineOptions({ name: 'FinanceBusinessOrderImportForm' });

const emit = defineEmits(['success']);

const selectedFile = ref<File | null>(null);
const bankAccount = ref('');
const importing = ref(false);
const result = ref<FinanceBusinessOrderApi.ImportResult | null>(null);

const errorColumns = [
  { title: '行号', dataIndex: 'rowNum', width: 80 },
  { title: '原因', dataIndex: 'reason' },
];

function mapImportErrorRows(rows: Record<number, string>) {
  return Object.entries(rows).map(([rowNum, reason]) => ({
    rowNum: Number(rowNum),
    reason,
  }));
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!selectedFile.value) {
      message.warning('请先选择 .xlsx 文件');
      return;
    }
    if (!bankAccount.value.trim()) {
      message.warning('请输入银行账号');
      return;
    }
    importing.value = true;
    modalApi.lock();
    try {
      result.value = await importBusinessOrder(
        selectedFile.value,
        bankAccount.value.trim(),
      );
      const hasErrors =
        Object.keys(result.value.failureRows).length > 0 ||
        result.value.skippedRows.length > 0;
      // Always refresh the grid whenever at least one record was written
      if (result.value.orderNos.length > 0) {
        emit('success');
      }
      if (!hasErrors) {
        message.success(
          `导入成功，共写入 ${result.value.orderNos.length} 条签单`,
        );
        await modalApi.close();
      }
      // Keep modal open when there are errors so user can review
    } finally {
      importing.value = false;
      modalApi.unlock();
    }
  },
  onClosed() {
    selectedFile.value = null;
    bankAccount.value = '';
    result.value = null;
  },
});

function beforeUpload(file: FileType) {
  selectedFile.value = file as File;
  result.value = null;
  return false;
}
</script>

<template>
  <Modal title="导入签单 Excel" class="w-[560px]">
    <div class="mx-4 space-y-4">
      <div class="flex items-center gap-2">
        <span class="shrink-0 text-sm text-gray-700">银行账号<span class="text-red-500">*</span></span>
        <Input
          v-model:value="bankAccount"
          placeholder="请输入银行账号（将应用于所有导入行）"
          allow-clear
        />
      </div>

      <Upload
        :before-upload="beforeUpload"
        :max-count="1"
        accept=".xlsx"
        :show-upload-list="!!selectedFile"
      >
        <Button type="primary">选择 .xlsx 文件</Button>
      </Upload>

      <template v-if="result">
        <!-- Success -->
        <Alert
          v-if="result.orderNos.length > 0"
          type="success"
          show-icon
          :message="`成功写入 ${result.orderNos.length} 条签单`"
          :description="result.orderNos.join('、')"
        />

        <!-- Skipped rows -->
        <Alert
          v-if="result.skippedRows.length > 0"
          type="warning"
          show-icon
          :message="`${result.skippedRows.length} 行已跳过（重复）：第 ${result.skippedRows.join('、')} 行`"
        />

        <!-- Errors -->
        <template v-if="Object.keys(result.failureRows).length > 0">
          <Alert
            type="error"
            show-icon
            :message="`${Object.keys(result.failureRows).length} 行导入失败，请修正后重新导入`"
          />
          <Table
            :data-source="mapImportErrorRows(result.failureRows)"
            :columns="errorColumns"
            :pagination="false"
            size="small"
            row-key="rowNum"
          />
        </template>
      </template>
    </div>
  </Modal>
</template>
