<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import type { SystemDeptImportApi } from '#/api/system/dept';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Table, Upload } from 'ant-design-vue';

import {
  getDeptImportTemplate,
  importDept,
  validateDeptImport,
} from '#/api/system/dept';

defineOptions({ name: 'SystemDeptImportForm' });

const emit = defineEmits<{ success: [] }>();

const selectedFile = ref<File | null>(null);
const validating = ref(false);
const committing = ref(false);
const preview = ref<SystemDeptImportApi.ImportResult | null>(null);

const errorColumns = [
  { title: '行号', dataIndex: 'rowNumber', width: 72 },
  { title: '组织路径', dataIndex: 'orgPath', width: 180, ellipsis: true },
  { title: '字段', dataIndex: 'field', width: 120 },
  { title: '错误码', dataIndex: 'code', width: 140 },
  { title: '说明', dataIndex: 'message', ellipsis: true },
];

const canCommit = computed(
  () =>
    !!selectedFile.value &&
    !!preview.value?.canCommit &&
    (preview.value?.errors?.length ?? 0) === 0,
);

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!selectedFile.value) {
      message.warning('请先选择 .xlsx 文件');
      return;
    }
    // 未预览：先校验
    if (!preview.value) {
      await runValidate();
      return;
    }
    if (!canCommit.value) {
      message.warning('存在校验错误，请修正后重新上传');
      return;
    }
    await runCommit();
  },
  onClosed() {
    selectedFile.value = null;
    preview.value = null;
    validating.value = false;
    committing.value = false;
  },
});

function beforeUpload(file: FileType) {
  const f = file as File;
  if (!f.name.toLowerCase().endsWith('.xlsx')) {
    message.error('仅支持 .xlsx 文件');
    return false;
  }
  selectedFile.value = f;
  preview.value = null;
  return false;
}

async function handleDownloadTemplate() {
  const data = await getDeptImportTemplate();
  downloadFileFromBlobPart({
    fileName: '组织架构导入模板.xlsx',
    source: data,
  });
}

async function runValidate() {
  if (!selectedFile.value) {
    message.warning('请先选择 .xlsx 文件');
    return;
  }
  validating.value = true;
  modalApi.lock();
  try {
    preview.value = await validateDeptImport(selectedFile.value);
    if (preview.value.canCommit) {
      message.success(
        `校验通过：将新增 ${preview.value.createCount}，跳过 ${preview.value.skipCount}`,
      );
    } else {
      message.warning(
        `校验失败 ${preview.value.errors?.length ?? 0} 项，整批不会写入`,
      );
    }
  } finally {
    validating.value = false;
    modalApi.unlock();
  }
}

async function runCommit() {
  if (!selectedFile.value || !preview.value?.fileDigest) {
    return;
  }
  committing.value = true;
  modalApi.lock();
  try {
    const result = await importDept(
      selectedFile.value,
      preview.value.fileDigest,
    );
    preview.value = result;
    if (result.canCommit) {
      message.success(
        `导入完成：新增 ${result.createCount}，跳过 ${result.skipCount}`,
      );
      emit('success');
      await modalApi.close();
    } else {
      message.warning(
        `提交未写入：${result.errors?.length ?? 0} 项错误（可能组织已被他人变更）`,
      );
    }
  } finally {
    committing.value = false;
    modalApi.unlock();
  }
}

function exportErrorsCsv() {
  const errors = preview.value?.errors ?? [];
  if (errors.length === 0) {
    message.info('当前无错误明细');
    return;
  }
  const header = ['rowNumber', 'orgPath', 'field', 'code', 'message'];
  const lines = [
    header.join(','),
    ...errors.map((e) =>
      [
        e.rowNumber,
        csvEscape(e.orgPath ?? ''),
        csvEscape(e.field ?? ''),
        csvEscape(e.code ?? ''),
        csvEscape(e.message ?? ''),
      ].join(','),
    ),
  ];
  // UTF-8 BOM，便于 Excel 打开中文
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], {
    type: 'text/csv;charset=utf-8',
  });
  downloadFileFromBlobPart({
    fileName: '组织导入错误明细.csv',
    source: blob,
  });
}

function csvEscape(value: string) {
  if (/[",\n]/.test(value)) {
    return `"${value.replaceAll('"', '""')}"`;
  }
  return value;
}

const confirmLabel = computed(() => {
  if (!preview.value) {
    return validating.value ? '校验中…' : '校验预览';
  }
  if (!canCommit.value) {
    return '存在错误';
  }
  return committing.value ? '导入中…' : '确认导入';
});
</script>

<template>
  <Modal
    title="导入组织"
    class="w-[780px]"
    :confirm-text="confirmLabel"
    :confirm-disabled="!!preview && !canCommit"
  >
    <div class="mx-4 space-y-4">
      <Alert
        type="info"
        show-icon
        message="仅新增公司/部门树：校验预览通过后整批原子提交"
        description="同路径且字段一致则跳过；字段冲突、缺父节点、公司挂部门等整批零写入。不含岗位/用户/员工挂靠。单文件 ≤1000 行 / 2MB / 深度 20 / 仅 xlsx。"
      />

      <Upload
        :before-upload="beforeUpload"
        :max-count="1"
        accept=".xlsx"
        :show-upload-list="!!selectedFile"
      >
        <Button type="primary" data-testid="dept-import-select-file">
          选择 .xlsx 文件
        </Button>
      </Upload>

      <template v-if="preview">
        <Alert
          :type="preview.canCommit ? 'success' : 'error'"
          show-icon
          :message="
            preview.canCommit
              ? `可提交：共 ${preview.totalRows} 行，新增 ${preview.createCount}，跳过 ${preview.skipCount}`
              : `不可提交：共 ${preview.totalRows} 行，错误 ${preview.errors?.length ?? 0} 项`
          "
        />
        <template v-if="(preview.errors?.length ?? 0) > 0">
          <div class="flex justify-end">
            <Button size="small" @click="exportErrorsCsv">
              导出错误 CSV
            </Button>
          </div>
          <Table
            :data-source="preview.errors"
            :columns="errorColumns"
            :pagination="false"
            size="small"
            row-key="rowNumber"
            data-testid="dept-import-error-table"
          />
        </template>
      </template>
    </div>

    <template #prepend-footer>
      <div class="flex flex-auto items-center gap-2">
        <Button data-testid="dept-import-download-template" @click="handleDownloadTemplate">
          下载导入模板
        </Button>
        <Button
          v-if="selectedFile && !preview"
          :loading="validating"
          data-testid="dept-import-validate"
          @click="runValidate"
        >
          仅校验
        </Button>
      </div>
    </template>
  </Modal>
</template>
