<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AttachmentApi } from '#/api/common/attachment';

import { computed, nextTick, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { uploadFile } from '#/api/infra/file';

import {
  createAttachmentFromUpload,
  useAttachmentActions,
  useAttachmentColumns,
} from './data';

interface Props {
  /** 附件列表 */
  modelValue?: AttachmentApi.AttachmentSaveReq[];
  /** 是否只读 */
  readonly?: boolean;
  /** 最大文件数量 */
  maxCount?: number;
  /** 允许的文件类型 */
  accept?: string;
  /** 最大文件大小（MB） */
  maxSize?: number;
  /** 隐藏上传按钮（当需要在外部自定义按钮位置时使用） */
  hideUploadButton?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  readonly: false,
  maxCount: 10,
  accept: '*',
  maxSize: 10,
  hideUploadButton: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: AttachmentApi.AttachmentSaveReq[]];
}>();

/** 表格内部数据 */
const tableData = ref<AttachmentApi.AttachmentSaveReq[]>([]);
/** 上传中 */
const uploading = ref(false);

/**
 * 实际上传文件到 /infra/file/upload，成功后再写入列表。
 * 返回服务端持久化 URL，刷新后仍可下载。
 */
async function handleAdd(file: File) {
  const uploadedUrl = await uploadFile({
    file,
    directory: 'common-attachment',
  });
  // requestClient.upload 解包后通常为 string URL；兼容 { url } 形态
  const url =
    typeof uploadedUrl === 'string'
      ? uploadedUrl
      : (uploadedUrl as any)?.url || (uploadedUrl as any)?.data;
  if (!url || typeof url !== 'string' || url.startsWith('blob:')) {
    throw new Error('文件上传失败：未获得服务端文件地址');
  }
  const attachment = createAttachmentFromUpload(
    file,
    tableData.value.length + 1,
    { url },
  );
  tableData.value.push(attachment);
  handleUpdateValue();
  message.success('文件上传成功');
}

/** 删除附件 */
function handleDelete(row: AttachmentApi.AttachmentSaveReq) {
  const index = tableData.value.findIndex(
    (item) =>
      (item.id && item.id === row.id) ||
      (item.fileName === row.fileName && item.uploadTime === row.uploadTime),
  );
  if (index !== -1) {
    tableData.value.splice(index, 1);
    // 重新排序
    tableData.value.forEach((item, idx) => {
      item.sortOrder = idx + 1;
    });
    handleUpdateValue();
    message.success('删除成功');
  }
}

/** 预览附件 */
function handlePreview(row: AttachmentApi.AttachmentSaveReq) {
  window.open(row.fileUrl, '_blank');
}

/** 下载附件 */
function handleDownload(row: AttachmentApi.AttachmentSaveReq) {
  const link = document.createElement('a');
  link.href = row.fileUrl;
  link.download = row.fileName;
  link.click();
}

/** 将最新数据写回并通知父组件 */
function handleUpdateValue() {
  emit('update:modelValue', [...tableData.value]);
}

/** 备注编辑完成后更新数据 */
function handleRemarkEdit() {
  handleUpdateValue();
}

function validateFileClient(file: File): string | undefined {
  if (file.size / 1024 / 1024 > props.maxSize) {
    return `文件大小不能超过 ${props.maxSize}MB`;
  }
  if (tableData.value.length >= props.maxCount) {
    return `最多只能上传 ${props.maxCount} 个文件`;
  }
  if (props.accept && props.accept !== '*') {
    const allowed = props.accept
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);
    const ext = file.name.includes('.')
      ? `.${file.name.split('.').pop()!.toLowerCase()}`
      : '';
    const mime = (file.type || '').toLowerCase();
    const ok = allowed.some(
      (a) => a === ext || a === mime || (a.endsWith('/*') && mime.startsWith(a.replace('/*', '/'))),
    );
    if (!ok) {
      return `仅支持文件类型：${props.accept}`;
    }
  }
  return undefined;
}

/** 校验并上传单个文件 */
async function handleFileUpload(file: File) {
  const err = validateFileClient(file);
  if (err) {
    message.error(err);
    return false;
  }
  try {
    uploading.value = true;
    await handleAdd(file);
    return true;
  } catch (e: any) {
    console.error(e);
    message.error(e?.message || '文件上传失败');
    return false;
  } finally {
    uploading.value = false;
  }
}

/** 触发文件选择（供外部调用） */
function handleTriggerUpload() {
  if (uploading.value) {
    message.warning('文件上传中，请稍候');
    return;
  }
  const input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.accept = props.accept === '*' ? '' : props.accept;
  input.addEventListener('change', async (e) => {
    const files = (e.target as HTMLInputElement).files;
    if (!files) return;
    for (const file of [...files]) {
      // 串行上传，避免并发超限
      // eslint-disable-next-line no-await-in-loop
      await handleFileUpload(file);
    }
  });
  input.click();
}

// 上传按钮配置
const uploadActions = computed(() => {
  if (
    props.readonly ||
    tableData.value.length >= props.maxCount ||
    props.hideUploadButton
  ) {
    return [];
  }

  return [
    {
      label: '上传附件',
      type: 'primary' as const,
      onClick: handleTriggerUpload,
    },
  ];
});

// 暴露方法给父组件
defineExpose({
  handleTriggerUpload,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    editConfig: {
      trigger: 'click',
      mode: 'cell',
    },
    columns: useAttachmentColumns(props.readonly),
    data: tableData.value,
    // 完全移除高度限制，让表格完全自适应
    height: undefined,
    maxHeight: undefined,
    border: true,
    showOverflow: true,
    autoResize: true,
    keepSource: true,
    // 禁用所有滚动相关配置
    scrollY: {
      enabled: false,
    },
    scrollX: {
      enabled: false,
    },
    // 禁用虚拟滚动
    virtualScrollY: false,
    virtualScrollX: false,
    rowConfig: {
      keyField: 'rowKey',
      isHover: true,
    },
    pagerConfig: {
      enabled: false,
    },
    toolbarConfig: {
      enabled: false,
    },
  } as VxeTableGridOptions<AttachmentApi.AttachmentSaveReq>,
  gridEvents: {
    editClosed: handleRemarkEdit,
  },
});

/** 监听 readonly 变化，动态更新列配置 */
watch(
  () => props.readonly,
  async (readonly) => {
    await nextTick();
    // 重新设置列配置
    const columns = useAttachmentColumns(readonly);
    if (columns) {
      gridApi.grid.reloadColumn(columns);
    }
  },
);

/** 监听外部传入的数据变化 */
watch(
  () => props.modelValue,
  async (attachments) => {
    if (!attachments) {
      return;
    }
    await nextTick();
    tableData.value = [...attachments];
    await gridApi.grid.reloadData(tableData.value);
  },
  {
    immediate: true,
    deep: true,
  },
);
</script>

<template>
  <div class="attachment-list">
    <!-- 上传区域 -->
    <div v-if="uploadActions.length > 0" class="mb-2 flex justify-end">
      <TableAction :actions="uploadActions" />
    </div>

    <!-- 附件列表 -->
    <div>
      <Grid class="w-full">
        <template #actions="{ row }">
          <TableAction
            :actions="
              useAttachmentActions(
                props.readonly,
                () => handlePreview(row),
                () => handleDownload(row),
                () => handleDelete(row),
              )
            "
          />
        </template>
      </Grid>
    </div>
  </div>
</template>

<style scoped>
.attachment-list {
  width: 100%;
}

.attachment-list :deep(.vxe-grid) {
  height: auto !important;
  max-height: none !important;
  padding-right: 0 !important;
  padding-left: 0 !important;
}

/* 确保按钮容器与表格对齐 */
.attachment-list > div {
  padding: 0;
  margin: 0;
}
</style>
