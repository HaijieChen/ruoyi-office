<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { FinanceBankReceiptApi } from '#/api/finance/receipt';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Table, Upload } from 'ant-design-vue';

import {
  importBankReceipt,
  importBankReceiptTemplate,
  mapFailureRows,
} from '#/api/finance/receipt';

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
      } else if (result.value.receiptNos.length > 0) {
        // 部分成功也刷新列表
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

async function handleDownloadTemplate() {
  const data = await importBankReceiptTemplate();
  downloadFileFromBlobPart({
    fileName: '银行到款导入模板.xls',
    source: data,
  });
}

const failureColumns = [
  { title: '行号', dataIndex: 'rowNum', width: 80 },
  { title: '失败原因', dataIndex: 'reason' },
];
</script>

<template>
  <Modal title="导入银行到款" class="w-2/5">
    <div class="mx-4 space-y-4">
      <Alert
        type="info"
        show-icon
        message="请先下载导入模板，按表头填写后上传 .xls/.xlsx 文件"
        description="必填：主体公司（首列，须与组织架构启用公司名称精确匹配）、银行账户、交易日期、交易金额、银行流水号。业务款时付款方名称必填，非业务款可不填。银行流水号不可重复。"
      />

      <Upload
        :before-upload="beforeUpload"
        :max-count="1"
        accept=".xls,.xlsx"
        :show-upload-list="!!selectedFile"
      >
        <Button type="primary">选择 Excel 文件</Button>
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

    <template #prepend-footer>
      <div class="flex flex-auto items-center">
        <Button @click="handleDownloadTemplate">下载导入模板</Button>
      </div>
    </template>
  </Modal>
</template>
