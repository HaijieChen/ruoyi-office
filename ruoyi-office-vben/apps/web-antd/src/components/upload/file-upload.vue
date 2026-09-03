<script lang="ts" setup>
import type { UploadFile, UploadProps } from 'ant-design-vue';
import type { UploadRequestOption } from 'ant-design-vue/lib/vc-upload/interface';

import type { FileUploadProps } from './typing';

import type { AxiosProgressEvent } from '#/api/infra/file';

import { computed, nextTick, onBeforeUnmount, ref, toRefs, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { $t } from '@vben/locales';
import { checkFileType, isFunction, isObject, isString } from '@vben/utils';

import { Button, Modal, message, Upload } from 'ant-design-vue';

import type { PreviewKind } from '#/utils/file-preview';

import {
  destroyDocxPreview,
  downloadAuthFile,
  fetchPreviewBlob,
  guessPreviewKind,
  renderDocxPreview,
  resolvePreviewKind,
  sniffPreviewKind,
} from '#/utils/file-preview';

import { UploadResultStatus } from './typing';
import { useUpload, useUploadType } from './use-upload';

defineOptions({ name: 'FileUpload', inheritAttrs: false });

const props = withDefaults(defineProps<FileUploadProps>(), {
  value: () => [],
  modelValue: undefined,
  directory: undefined,
  disabled: false,
  drag: false,
  helpText: '',
  listType: 'text',
  maxSize: 2,
  maxNumber: 1,
  accept: () => [],
  multiple: false,
  api: undefined,
  resultField: '',
  showDescription: false,
});
const emit = defineEmits([
  'change',
  'update:value',
  'update:modelValue',
  'delete',
  'returnText',
  'preview',
  'uploaded',
]);
const { accept, helpText, maxNumber, maxSize } = toRefs(props);
const isInnerOperate = ref<boolean>(false);
const { getStringAccept } = useUploadType({
  acceptRef: accept,
  helpTextRef: helpText,
  maxNumberRef: maxNumber,
  maxSizeRef: maxSize,
});

/** 计算当前绑定的值，优先使用 modelValue */
const currentValue = computed(() => {
  return props.modelValue === undefined ? props.value : props.modelValue;
});

/** 判断是否使用 modelValue */
const isUsingModelValue = computed(() => {
  return props.modelValue !== undefined;
});

const fileList = ref<UploadProps['fileList']>([]);
const blobThumbs = new Map<string, string>();

function isImageFile(file: { name?: string; type?: string; url?: string }) {
  if (String(file.type || '').startsWith('image/')) return true;
  return guessPreviewKind(file.url || file.name || '') === 'image';
}

async function hydrateImageThumbs(files?: UploadProps['fileList']) {
  if (!files?.length || props.listType === 'text') return;
  for (const file of files) {
    const url = file.url;
    if (!url || !isImageFile(file) || file.thumbUrl?.startsWith('blob:')) continue;
    try {
      const blob = await fetchPreviewBlob(url);
      const obj = URL.createObjectURL(blob);
      const prev = blobThumbs.get(url);
      if (prev) URL.revokeObjectURL(prev);
      blobThumbs.set(url, obj);
      file.thumbUrl = obj;
    } catch {
      // keep original url; img may still fail without auth
    }
  }
}

onBeforeUnmount(() => {
  for (const obj of blobThumbs.values()) URL.revokeObjectURL(obj);
  blobThumbs.clear();
});

const isLtMsg = ref<boolean>(true); // 文件大小错误提示
const isActMsg = ref<boolean>(true); // 文件类型错误提示
const isFirstRender = ref<boolean>(true); // 是否第一次渲染
const uploadNumber = ref<number>(0); // 上传文件计数器
const uploadList = ref<any[]>([]); // 临时上传列表
const previewOpen = ref(false);
const previewTitle = ref('预览');
const previewSrc = ref('');
const previewKind = ref<PreviewKind>('image');
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

watch(
  currentValue,
  (v) => {
    if (isInnerOperate.value) {
      isInnerOperate.value = false;
      return;
    }
    let value: string[] = [];
    if (v) {
      if (Array.isArray(v)) {
        value = v;
      } else {
        value.push(v);
      }
      fileList.value = value
        .map((item, i) => {
          if (item && isString(item)) {
            const kind = guessPreviewKind(item);
            return {
              uid: `${-i}`,
              name: item.slice(Math.max(0, item.lastIndexOf('/') + 1)),
              status: UploadResultStatus.DONE,
              url: item,
              type: kind === 'image' ? 'image/jpeg' : undefined,
            };
          } else if (item && isObject(item)) {
            return item;
          }
          return null;
        })
        .filter(Boolean) as UploadProps['fileList'];
      void hydrateImageThumbs(fileList.value);
    }
    if (!isFirstRender.value) {
      emit('change', value);
      isFirstRender.value = false;
    }
  },
  {
    immediate: true,
    deep: true,
  },
);

/** 处理文件删除 */
async function handleRemove(file: UploadFile) {
  if (fileList.value) {
    const index = fileList.value.findIndex((item) => item.uid === file.uid);
    index !== -1 && fileList.value.splice(index, 1);
    const value = getValue();
    isInnerOperate.value = true;
    emit('update:value', value);
    emit('update:modelValue', value);
    emit('change', value);
    emit('delete', file);
  }
}

async function handleDownload(file: UploadFile) {
  const url = file.url || '';
  if (file.originFileObj) {
    const obj = URL.createObjectURL(file.originFileObj);
    const a = document.createElement('a');
    a.href = obj;
    a.download = file.name || 'file';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(obj), 1000);
    return;
  }
  if (!url) {
    message.warning('没有可下载的地址');
    return;
  }
  try {
    await downloadAuthFile(url, file.name);
  } catch {
    message.error('下载失败');
  }
}

/** 处理文件预览 */
async function handlePreview(file: UploadFile) {
  emit('preview', file);
  let kind = resolvePreviewKind({
    name: file.name,
    url: file.url,
    type: file.type,
    originName: file.originFileObj?.name,
  });
  const gen = ++previewGen;
  try {
    let blob: Blob | undefined = file.originFileObj;
    if (!kind) {
      if (!blob) {
        const url = file.url || '';
        if (!url) {
          message.info('该文件类型不支持在线预览');
          return;
        }
        blob = await fetchPreviewBlob(url);
      }
      kind = await sniffPreviewKind(blob);
    }
    if (!kind) {
      message.info('该文件类型不支持在线预览');
      return;
    }
    if (kind === 'docx') {
      if (!blob) {
        const url = file.url || '';
        if (!url) {
          message.warning('没有可预览的地址');
          return;
        }
        blob = await fetchPreviewBlob(url);
      }
      if (gen !== previewGen) return;
      previewKind.value = 'docx';
      previewTitle.value = file.name || '预览';
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
    if (file.originFileObj) {
      const local = URL.createObjectURL(file.originFileObj);
      previewObjectUrl = local;
      previewSrc.value = local;
      previewKind.value = kind;
      previewTitle.value = file.name || '预览';
      previewOpen.value = true;
      return;
    }
    const url = file.url || '';
    if (!url) {
      message.warning('没有可预览的地址');
      return;
    }
    const remote = blob ?? (await fetchPreviewBlob(url));
    const obj = URL.createObjectURL(remote);
    previewObjectUrl = obj;
    previewSrc.value = obj;
    previewKind.value = kind;
    previewTitle.value = file.name || '预览';
    previewOpen.value = true;
  } catch {
    if (gen !== previewGen) return;
    closePreview();
    message.error('无法在本页预览该文件');
  }
}

/** 处理文件数量超限 */
function handleExceed() {
  message.error($t('ui.upload.maxNumber', [maxNumber.value]));
}

/** 处理上传错误 */
function handleUploadError(error: any) {
  console.error('上传错误:', error);
  message.error($t('ui.upload.uploadError'));
  // 上传失败时减少计数器
  uploadNumber.value = Math.max(0, uploadNumber.value - 1);
}

/**
 * 上传前校验
 * @param file 待上传的文件
 * @returns 是否允许上传
 */
async function beforeUpload(file: File) {
  const fileContent = await file.text();
  emit('returnText', fileContent);

  // 检查文件数量限制
  if (fileList.value!.length >= props.maxNumber) {
    message.error($t('ui.upload.maxNumber', [props.maxNumber]));
    return Upload.LIST_IGNORE;
  }

  const { maxSize, accept } = props;
  const isAct = checkFileType(file, accept);
  if (!isAct) {
    message.error($t('ui.upload.acceptUpload', [accept]));
    isActMsg.value = false;
    // 防止弹出多个错误提示
    setTimeout(() => (isActMsg.value = true), 1000);
    return Upload.LIST_IGNORE;
  }
  const isLt = file.size / 1024 / 1024 > maxSize;
  if (isLt) {
    message.error($t('ui.upload.maxSizeMultiple', [maxSize]));
    isLtMsg.value = false;
    // 防止弹出多个错误提示
    setTimeout(() => (isLtMsg.value = true), 1000);
    return Upload.LIST_IGNORE;
  }

  // 只有在验证通过后才增加计数器
  uploadNumber.value++;
  return true;
}

/** 自定义上传请求 */
async function customRequest(info: UploadRequestOption) {
  let { api } = props;
  if (!api || !isFunction(api)) {
    api = useUpload(props.directory).httpRequest;
  }
  try {
    // 上传文件
    const progressEvent: AxiosProgressEvent = (e) => {
      const percent = Math.trunc((e.loaded / e.total!) * 100);
      info.onProgress!({ percent });
    };
    const res = await api?.(info.file as File, progressEvent);

    // 处理上传成功后的逻辑
    handleUploadSuccess(res, info.file as File);

    info.onSuccess!(res);
    message.success($t('ui.upload.uploadSuccess'));
  } catch (error: any) {
    console.error(error);
    info.onError!(error);
    handleUploadError(error);
  }
}

/**
 * 处理上传成功
 * @param res 上传响应结果
 * @param file 上传的文件
 */
function handleUploadSuccess(res: any, file: File) {
  // 删除临时文件
  const index = fileList.value?.findIndex((item) => item.name === file.name);
  if (index !== -1) {
    fileList.value?.splice(index!, 1);
  }

  // 添加到临时上传列表
  const fileUrl = res?.url || res?.data || res;
  uploadList.value.push({
    name: file.name,
    url: fileUrl,
    status: UploadResultStatus.DONE,
    uid: file.name + Date.now(),
  });

  // 检查是否所有文件都上传完成
  if (uploadList.value.length >= uploadNumber.value) {
    fileList.value?.push(...uploadList.value);
    uploadList.value = [];
    uploadNumber.value = 0;

    // 更新值
    const value = getValue();
    isInnerOperate.value = true;
    emit('update:value', value);
    emit('update:modelValue', value);
    emit('change', value);
    emit('uploaded', { url: fileUrl, name: file.name });
  }
}

/**
 * 获取当前文件列表的值
 * @returns 文件 URL 列表或字符串
 */
function getValue() {
  const list = (fileList.value || [])
    .filter((item) => item?.status === UploadResultStatus.DONE)
    .map((item: any) => {
      if (item?.response && props?.resultField) {
        return item?.response;
      }
      return item?.url || item?.response?.url || item?.response;
    });

  // 单个文件的情况，根据输入参数类型决定返回格式
  if (props.maxNumber === 1) {
    const singleValue = list.length > 0 ? list[0] : '';
    // 如果原始值是字符串或 modelValue 是字符串，返回字符串
    if (
      isString(props.value) ||
      (isUsingModelValue.value && isString(props.modelValue))
    ) {
      return singleValue;
    }
    return singleValue;
  }

  // 多文件情况，根据输入参数类型决定返回格式
  if (isUsingModelValue.value) {
    return Array.isArray(props.modelValue) ? list : list.join(',');
  }

  return Array.isArray(props.value) ? list : list.join(',');
}
</script>

<template>
  <div
    :class="[
      'file-upload-root',
      listType !== 'text' ? 'file-upload-root--thumb' : '',
    ]"
  >
    <Upload
      v-bind="$attrs"
      v-model:file-list="fileList"
      :accept="getStringAccept"
      :before-upload="beforeUpload"
      :custom-request="customRequest"
      :disabled="disabled"
      :max-count="maxNumber"
      :multiple="multiple"
      :list-type="listType"
      :progress="{ showInfo: true }"
      :show-upload-list="{
        showPreviewIcon: true,
        showRemoveIcon: !disabled,
        showDownloadIcon: listType === 'text',
      }"
      @remove="handleRemove"
      @preview="handlePreview"
      @download="handleDownload"
      @reject="handleExceed"
    >
      <div v-if="drag" class="upload-drag-area">
        <p class="ant-upload-drag-icon">
          <IconifyIcon icon="lucide:cloud-upload" />
        </p>
        <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
        <p class="ant-upload-hint">
          支持{{ accept.join('/') }}格式文件，不超过{{ maxSize }}MB
        </p>
      </div>
      <div v-else-if="fileList && fileList.length < maxNumber">
        <div
          v-if="listType === 'picture-card' || listType === 'picture'"
          class="flex h-full flex-col items-center justify-center"
        >
          <IconifyIcon icon="lucide:plus" />
        </div>
        <Button v-else>
          <IconifyIcon icon="lucide:cloud-upload" />
          {{ $t('ui.upload.upload') }}
        </Button>
      </div>
      <div
        v-if="showDescription && !drag"
        class="mt-2 flex flex-wrap items-center"
      >
        请上传不超过
        <div class="mx-1 font-bold text-primary">{{ maxSize }}MB</div>
        的
        <div class="mx-1 font-bold text-primary">{{ accept.join('/') }}</div>
        格式文件
      </div>
      <template v-if="listType !== 'text'" #itemRender="{ file, actions }">
        <button
          type="button"
          class="file-upload-thumb"
          :title="file.name"
          @click="actions.preview()"
        >
          <img
            v-if="isImageFile(file)"
            :src="file.thumbUrl || file.url"
            :alt="file.name"
          />
          <IconifyIcon v-else icon="lucide:file-text" />
        </button>
      </template>
    </Upload>
    <Modal
      :open="previewOpen"
      :title="previewTitle"
      :footer="null"
      :width="previewKind === 'docx' ? 'min(96vw, 1120px)' : '720px'"
      destroy-on-close
      :body-style="previewKind === 'docx' ? { overflow: 'auto' } : undefined"
      @cancel="closePreview"
    >
      <img
        v-if="previewKind === 'image'"
        :src="previewSrc"
        :alt="previewTitle"
        class="max-h-[70vh] w-full object-contain"
      />
      <iframe
        v-else-if="previewKind === 'pdf'"
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
.file-upload-root--thumb :deep(.ant-upload-list-item-name) {
  display: none;
}

.file-upload-thumb {
  display: flex;
  width: 56px;
  height: 56px;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  background: #fafafa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  align-items: center;
  justify-content: center;
}

.file-upload-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file-upload-root--thumb :deep(.ant-upload-select-picture-card),
.file-upload-root--thumb :deep(.ant-upload-list-picture-card .ant-upload-list-item) {
  width: 56px;
  height: 56px;
  margin-block: 0;
  margin-inline-end: 8px;
}

.file-upload-root--thumb :deep(.ant-upload-list-picture-card-container) {
  width: 56px;
  height: 56px;
  margin-block: 0;
  margin-inline-end: 8px;
}

.upload-drag-area {
  padding: 20px;
  text-align: center;
  background-color: #fafafa;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  transition: border-color 0.3s;
}

.upload-drag-area:hover {
  border-color: hsl(var(--primary));
}

.ant-upload-drag-icon {
  margin-bottom: 16px;
  font-size: 48px;
  color: #d9d9d9;
}

.ant-upload-text {
  margin-bottom: 8px;
  font-size: 16px;
  color: #666;
}

.ant-upload-hint {
  font-size: 14px;
  color: #999;
}
</style>
