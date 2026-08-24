<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Table, Upload } from 'ant-design-vue';

import {
  importCompanyBankAccount,
  importCompanyBankAccountTemplate,
} from '#/api/finance/company-bank-account';

defineOptions({ name: 'FinanceCompanyBankAccountImportForm' });

const emit = defineEmits(['success']);

const selectedFile = ref<File | null>(null);
const importing = ref(false);
const result = ref<{
  createdNos: string[];
  failureRows: Record<number, string>;
} | null>(null);

const errorColumns = [
  { title: '行号', dataIndex: 'rowNum', width: 80 },
  { title: '原因', dataIndex: 'reason' },
];

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!selectedFile.value) {
      message.warning('请先选择 Excel 文件');
      return;
    }
    importing.value = true;
    modalApi.lock();
    try {
      result.value = await importCompanyBankAccount(selectedFile.value);
      if (result.value.createdNos.length > 0) {
        emit('success');
      }
      if (Object.keys(result.value.failureRows).length === 0) {
        message.success(`导入成功，共写入 ${result.value.createdNos.length} 条`);
        await modalApi.close();
      }
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
  const data = await importCompanyBankAccountTemplate();
  downloadFileFromBlobPart({
    fileName: '账户信息导入模板.xls',
    source: data,
  });
}
</script>

<template>
  <Modal title="导入账户信息" class="w-[560px]">
    <div class="mx-4 space-y-4">
      <Alert
        type="info"
        show-icon
        message="主体公司须与组织架构公司名称完全一致。同一主体下账号已存在则该行失败，不覆盖已有账户。"
      />
      <Button @click="handleDownloadTemplate">下载模板</Button>
      <Upload :before-upload="beforeUpload" :max-count="1" accept=".xls,.xlsx">
        <Button>选择文件</Button>
      </Upload>
      <Table
        v-if="result && Object.keys(result.failureRows).length"
        size="small"
        :pagination="false"
        :columns="errorColumns"
        :data-source="
          Object.entries(result.failureRows).map(([rowNum, reason]) => ({
            rowNum: Number(rowNum),
            reason,
          }))
        "
      />
    </div>
  </Modal>
</template>
