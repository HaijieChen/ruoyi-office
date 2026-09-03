<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AttachmentApi } from '#/api/common/attachment';

import { computed, nextTick, ref, watch } from 'vue';

import type { PreviewKind } from '#/utils/file-preview';

import {
  destroyDocxPreview,
  fetchPreviewBlob,
  guessPreviewKind,
  renderDocxPreview,
} from '#/utils/file-preview';

import { Modal, message } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import { uploadFile } from '#/api/infra/file';
import { uploadOnboardingFile } from '#/api/hrm/employee';
import { useAccessStore } from '@vben/stores';
import { isTenantEnable, useAppConfig } from '@vben/hooks';

import { resolveRequestTenantId } from '#/constants/tenant';

import { createAttachmentFromOnboardingClaim } from './onboarding-claim';
import { useAttachmentActions, useAttachmentColumns } from './data';

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
  /**
   * 入职资料：走 HRM 专用 upload + 一次性 claimToken（不返回公开 URL）
   */
  useFileClaim?: boolean;
  /**
   * 鉴权下载：传入相对路径字段名（如 downloadPath）时走带 token 的下载
   */
  authDownload?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  readonly: false,
  maxCount: 10,
  accept: '*',
  maxSize: 10,
  hideUploadButton: false,
  useFileClaim: false,
  authDownload: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: AttachmentApi.AttachmentSaveReq[]];
}>();

/** 表格内部数据 */
const tableData = ref<AttachmentApi.AttachmentSaveReq[]>([]);
/** 上传中 */
const uploading = ref(false);

/**
 * 实际上传：useFileClaim 时走 HRM onboarding-file/upload 拿一次性 claimToken。
 */
async function handleAdd(file: File) {
  if (props.useFileClaim) {
    const claim = await uploadOnboardingFile(file);
    if (!claim?.claimToken) {
      throw new Error('文件上传失败：未获得作用域 claimToken');
    }
    const attachment = createAttachmentFromOnboardingClaim(
      file,
      tableData.value.length + 1,
      claim,
    );
    tableData.value.push(attachment as any);
  } else {
    // 通用业务：仍上传到文件服务，但不强制 fileId claim（兼容 DOCX 等）
    const uploadedUrl = await uploadFile({
      file,
      directory: 'common-attachment',
    });
    const url =
      typeof uploadedUrl === 'string'
        ? uploadedUrl
        : (uploadedUrl as any)?.url || (uploadedUrl as any)?.data;
    if (!url || typeof url !== 'string' || url.startsWith('blob:')) {
      throw new Error('文件上传失败：未获得服务端文件地址');
    }
    tableData.value.push({
      id: undefined,
      businessType: '',
      businessId: 0,
      fileName: file.name,
      filePath: url,
      fileUrl: url,
      fileSize: file.size,
      fileType: file.type,
      fileExtension: file.name.includes('.')
        ? file.name.split('.').pop()!.toLowerCase()
        : '',
      uploadTime: new Date(),
      sortOrder: tableData.value.length + 1,
      remark: '',
    } as any);
  }
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

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const accessStore = useAccessStore();

function resolveAuthUrl(row: any): string | undefined {
  const path = row.downloadPath as string | undefined;
  if (!path) return undefined;
  const base = apiURL?.replace(/\/$/, '') || '';
  return path.startsWith('http')
    ? path
    : `${base}${path.startsWith('/') ? '' : '/'}${path}`;
}

function resolveLocalPreviewUrl(row: any): string | undefined {
  const local = row.localPreviewUrl as string | undefined;
  return local && local.startsWith('blob:') ? local : undefined;
}

const PREVIEW_MIME: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  gif: 'image/gif',
  bmp: 'image/bmp',
  webp: 'image/webp',
};

function fileExt(row: any): string {
  const fromName =
    typeof row?.fileName === 'string' && row.fileName.includes('.')
      ? row.fileName.split('.').pop()
      : '';
  return String(row?.fileExtension || fromName || '')
    .replace(/^\./, '')
    .toLowerCase();
}

function previewKind(row: any): PreviewKind | null {
  const ext = fileExt(row);
  return guessPreviewKind(row.fileName || (ext ? `file.${ext}` : '') || row.fileUrl || '');
}

const previewOpen = ref(false);
const previewTitle = ref('预览');
const previewSrc = ref('');
const previewType = ref<PreviewKind>('image');
const previewLoading = ref(false);
const docxContainer = ref<HTMLElement | null>(null);
let previewObjectUrl = '';
let previewGen = 0;

function closePreview() {
  previewGen += 1;
  previewOpen.value = false;
  previewSrc.value = '';
  previewLoading.value = false;
  destroyDocxPreview(docxContainer.value);
  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl);
    previewObjectUrl = '';
  }
}

function openPreview(
  src: string,
  kind: 'image' | 'pdf',
  title: string,
  revoke = false,
) {
  closePreview();
  if (revoke) previewObjectUrl = src;
  previewSrc.value = src;
  previewType.value = kind;
  previewTitle.value = title || '预览';
  previewOpen.value = true;
}

function blobWithPreviewType(blob: Blob, row: any): Blob {
  const mime = PREVIEW_MIME[fileExt(row)];
  if (!mime || blob.type === mime) return blob;
  return new Blob([blob], { type: mime });
}

/** 带 Authorization / tenant-id 拉取鉴权下载地址 */
async function fetchAuthorizedBlob(row: any): Promise<Blob | null> {
  const url = resolveAuthUrl(row);
  if (!url) return null;
  const token = accessStore.accessToken;
  const headers: Record<string, string> = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (isTenantEnable()) {
    headers['tenant-id'] = String(resolveRequestTenantId(accessStore.tenantId));
  }
  const res = await fetch(url, {
    method: 'GET',
    headers,
    credentials: 'include',
  });
  if (!res.ok) {
    throw new Error(`下载失败 HTTP ${res.status}`);
  }
  return res.blob();
}

async function resolvePreviewBlob(
  row: AttachmentApi.AttachmentSaveReq,
): Promise<Blob | null> {
  const local = resolveLocalPreviewUrl(row);
  if (local) {
    const res = await fetch(local);
    if (!res.ok) throw new Error(`预览失败 HTTP ${res.status}`);
    return res.blob();
  }
  if (props.authDownload) {
    const blob = await fetchAuthorizedBlob(row);
    if (blob) return blob;
  }
  const url = row.fileUrl;
  if (url && !url.startsWith('blob:')) {
    return fetchPreviewBlob(url);
  }
  return null;
}

/** 预览：弹窗看图/PDF/DOCX。其它类型不支持在线预览，走下载。 */
async function handlePreview(row: AttachmentApi.AttachmentSaveReq) {
  const kind = previewKind(row);
  if (!kind) {
    message.info('该文件类型不支持在线预览，请下载后查看');
    return;
  }
  const gen = ++previewGen;
  try {
    if (kind === 'docx') {
      const blob = await resolvePreviewBlob(row);
      if (!blob) {
        message.warning('无法预览：缺少鉴权下载地址');
        return;
      }
      if (gen !== previewGen) return;
      previewType.value = 'docx';
      previewTitle.value = row.fileName || '预览';
      previewLoading.value = true;
      previewOpen.value = true;
      await nextTick();
      if (gen !== previewGen) return;
      const el = docxContainer.value;
      if (!el) throw new Error('docx container missing');
      await renderDocxPreview(blob, el);
      if (gen !== previewGen) return;
      previewLoading.value = false;
      return;
    }
    if (props.authDownload) {
      const local = resolveLocalPreviewUrl(row);
      if (local) {
        openPreview(local, kind, row.fileName || '预览');
        return;
      }
      const blob = await fetchAuthorizedBlob(row);
      if (blob) {
        openPreview(
          URL.createObjectURL(blobWithPreviewType(blob, row)),
          kind,
          row.fileName || '预览',
          true,
        );
        return;
      }
    }
    if (row.fileUrl && !row.fileUrl.startsWith('blob:')) {
      openPreview(row.fileUrl, kind, row.fileName || '预览');
    } else {
      message.warning('无法预览：缺少鉴权下载地址');
    }
  } catch (e: any) {
    if (gen !== previewGen) return;
    closePreview();
    message.error(e?.message || '预览失败（可能未登录或无权限）');
  }
}

/** 下载附件 */
async function handleDownload(row: AttachmentApi.AttachmentSaveReq) {
  try {
    if (props.authDownload) {
      const local = resolveLocalPreviewUrl(row);
      if (local) {
        const link = document.createElement('a');
        link.href = local;
        link.download = row.fileName || 'attachment';
        link.click();
        return;
      }
      const blob = await fetchAuthorizedBlob(row);
      if (blob) {
        const obj = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = obj;
        link.download = row.fileName || 'attachment';
        link.click();
        URL.revokeObjectURL(obj);
        return;
      }
    }
    if (row.fileUrl && !row.fileUrl.startsWith('blob:')) {
      const link = document.createElement('a');
      link.href = row.fileUrl;
      link.download = row.fileName;
      link.click();
    } else {
      message.warning('无法下载：缺少鉴权下载地址');
    }
  } catch (e: any) {
    message.error(e?.message || '下载失败（可能未登录或无权限）');
  }
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
      (a) =>
        a === ext ||
        a === mime ||
        (a.endsWith('/*') && mime.startsWith(a.replace('/*', '/'))),
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

    <Modal
      v-model:open="previewOpen"
      :title="previewTitle"
      :footer="null"
      width="860px"
      destroy-on-close
      @cancel="closePreview"
    >
      <img
        v-if="previewType === 'image'"
        :src="previewSrc"
        :alt="previewTitle"
        class="max-h-[70vh] w-full object-contain"
      />
      <iframe
        v-else-if="previewType === 'pdf'"
        :src="previewSrc"
        class="h-[70vh] w-full border-0"
        title="pdf-preview"
      />
      <div v-else class="relative">
        <div
          v-if="previewLoading"
          class="absolute inset-0 z-10 flex items-center justify-center bg-white/80"
        >
          加载中...
        </div>
        <div
          ref="docxContainer"
          role="document"
          :aria-label="previewTitle"
          tabindex="0"
          class="h-[70vh] w-full overflow-auto"
        ></div>
      </div>
    </Modal>
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
