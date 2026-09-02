<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { ContractApplicationImportResult } from '#/api/finance/contract-application';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Table, Upload } from 'ant-design-vue';

import {
  importContractApplication,
  importContractApplicationTemplate,
} from '#/api/finance/contract-application';

defineOptions({ name: 'FinanceContractApplicationImportForm' });

const emit = defineEmits(['success']);

const selectedFile = ref<File | null>(null);
const importing = ref(false);
const result = ref<ContractApplicationImportResult | null>(null);

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
      result.value = await importContractApplication(selectedFile.value);
      const hasErrors = Object.keys(result.value.failureRows).length > 0;
      // Always refresh the grid whenever at least one record was written
      if (result.value.createdNos.length > 0) {
        emit('success');
      }
      if (!hasErrors) {
        message.success(
          `导入成功，共写入 ${result.value.createdNos.length} 条合同签约`,
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
  const data = await importContractApplicationTemplate();
  downloadFileFromBlobPart({
    fileName: '合同签约导入模板.xls',
    source: data,
  });
}
</script>

<template>
  <Modal title="导入合同签约 Excel" class="w-[560px]">
    <div class="mx-4 space-y-4">
      <Alert
        type="info"
        show-icon
        message="请先下载导入模板，按表头填写后上传"
        description="导入后即为已通过，不走审批。对方客商按名称精确匹配已启用档案；主体公司按公司名称精确匹配；申请人填系统登录账号。合同业务单号可不填，空则自动生成；填写且已存在则该行失败。"
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
          v-if="result.createdNos.length > 0"
          type="success"
          show-icon
          :message="`成功写入 ${result.createdNos.length} 条合同签约`"
          :description="result.createdNos.join('、')"
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
