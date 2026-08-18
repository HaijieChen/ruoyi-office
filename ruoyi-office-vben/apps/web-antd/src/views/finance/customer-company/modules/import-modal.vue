<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { CustomerCompanyImportResult } from '#/api/finance/customer-company';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Table, Upload } from 'ant-design-vue';

import {
  importCustomerCompany,
  importCustomerCompanyTemplate,
} from '#/api/finance/customer-company';

defineOptions({ name: 'FinanceCustomerCompanyImportForm' });

const emit = defineEmits(['success']);

const selectedFile = ref<File | null>(null);
const importing = ref(false);
const result = ref<CustomerCompanyImportResult | null>(null);

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
    importing.value = true;
    modalApi.lock();
    try {
      result.value = await importCustomerCompany(selectedFile.value);
      const hasErrors = Object.keys(result.value.failureRows).length > 0;
      // Always refresh the grid whenever at least one record was written
      if (result.value.createdCodes.length > 0) {
        emit('success');
      }
      if (!hasErrors) {
        message.success(
          `导入成功，共写入 ${result.value.createdCodes.length} 条客户公司`,
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
    result.value = null;
  },
});

function beforeUpload(file: FileType) {
  selectedFile.value = file as File;
  result.value = null;
  return false;
}

async function handleDownloadTemplate() {
  const data = await importCustomerCompanyTemplate();
  downloadFileFromBlobPart({
    fileName: '客户公司导入模板.xls',
    source: data,
  });
}
</script>

<template>
  <Modal title="导入客户公司 Excel" class="w-[560px]">
    <div class="mx-4 space-y-4">
      <Alert
        type="info"
        show-icon
        message="请先下载导入模板，按表头填写后上传"
        description="税号租户内唯一。已存在则该行失败，不会更新档案。是否客户默认是，是否供应商默认否。供应商须填开户银行与银行账号。"
      />

      <Upload
        :before-upload="beforeUpload"
        :max-count="1"
        accept=".xls,.xlsx"
        :show-upload-list="!!selectedFile"
      >
        <Button type="primary">选择 .xlsx 文件</Button>
      </Upload>

      <template v-if="result">
        <!-- Success -->
        <Alert
          v-if="result.createdCodes.length > 0"
          type="success"
          show-icon
          :message="`成功写入 ${result.createdCodes.length} 条客户公司`"
          :description="result.createdCodes.join('、')"
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

    <template #prepend-footer>
      <div class="flex flex-auto items-center">
        <Button @click="handleDownloadTemplate">下载导入模板</Button>
      </div>
    </template>
  </Modal>
</template>
