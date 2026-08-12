<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { EmployeeRosterImportApi } from '#/api/hrm/employee';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Table, Upload } from 'ant-design-vue';

import {
  importEmployeeRoster,
  importEmployeeRosterTemplate,
} from '#/api/hrm/employee';

defineOptions({ name: 'HrmEmployeeRosterImportForm' });

const emit = defineEmits(['success']);

const selectedFile = ref<File | null>(null);
const importing = ref(false);
const result = ref<EmployeeRosterImportApi.ImportResult | null>(null);

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
      result.value = await importEmployeeRoster(selectedFile.value);
      const createCount = result.value.createNames?.length ?? 0;
      const updateCount = result.value.updateNames?.length ?? 0;
      const failCount = Object.keys(result.value.failureRows ?? {}).length;
      if (createCount + updateCount > 0) {
        emit('success');
      }
      if (failCount === 0) {
        message.success(
          `导入完成：新建 ${createCount} 人，更新 ${updateCount} 人`,
        );
        await modalApi.close();
      } else {
        message.warning(
          `部分成功：新建 ${createCount}，更新 ${updateCount}，失败 ${failCount} 行`,
        );
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
  const data = await importEmployeeRosterTemplate();
  downloadFileFromBlobPart({
    fileName: '文枢花名册导入模板.xlsx',
    source: data,
  });
}
</script>

<template>
  <Modal title="导入文枢花名册" class="w-[620px]">
    <div class="mx-4 space-y-4">
      <Alert
        type="info"
        show-icon
        message="请先下载官方导入模板，按第 2 行表头填写后上传"
        description="以身份证号 upsert（有则更新、无则新建）。序号/年龄/司龄不写库；入职资料列不携带附件本体。勿改动表头文案（含换行列）。"
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
        <Alert
          v-if="(result.createNames?.length ?? 0) > 0"
          type="success"
          show-icon
          :message="`新建 ${result.createNames.length} 人`"
          :description="result.createNames.join('、')"
        />
        <Alert
          v-if="(result.updateNames?.length ?? 0) > 0"
          type="success"
          show-icon
          :message="`更新 ${result.updateNames.length} 人`"
          :description="result.updateNames.join('、')"
        />
        <template v-if="Object.keys(result.failureRows ?? {}).length > 0">
          <Alert
            type="error"
            show-icon
            :message="`${Object.keys(result.failureRows).length} 行导入失败，请按行号修正后重导`"
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
