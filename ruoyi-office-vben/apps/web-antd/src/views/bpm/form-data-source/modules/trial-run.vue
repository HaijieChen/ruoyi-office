<script lang="ts" setup>
import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';

import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Alert,
  Button,
  Descriptions,
  DescriptionsItem,
  Input,
  message,
  Table,
} from 'ant-design-vue';

import {
  getDataSourceVersion,
  getDataSourceVersionList,
  trialRunDataSource,
} from '#/api/bpm/form-data-source';

import {
  parseSchemaFields,
  RESERVED_CONTEXT_PARAMETERS,
  sanitizeTrialRunError,
} from '../data';

const source = ref<BpmFormDataSourceApi.Definition>();
const draft = ref<BpmFormDataSourceApi.Version>();
const parameterValues = reactive<Record<string, string>>({});
const result = ref<BpmFormDataSourceApi.ExecuteResult>();
const durationMs = ref<number>();
const validationError = ref('');
const running = ref(false);

const parameters = computed(() =>
  parseSchemaFields(draft.value?.parameterSchema).filter(
    (field) => !RESERVED_CONTEXT_PARAMETERS.has(field.name),
  ),
);
const columns = computed(() => {
  const firstRow = result.value?.rows?.[0];
  return Object.keys(firstRow ?? {}).map((key) => ({
    dataIndex: key,
    key,
    title: key,
  }));
});

function reset() {
  source.value = undefined;
  draft.value = undefined;
  result.value = undefined;
  durationMs.value = undefined;
  validationError.value = '';
  for (const key of Object.keys(parameterValues)) delete parameterValues[key];
}

function coerceValue(value: string, type: string) {
  if (value === '') return undefined;
  if (['DECIMAL', 'INTEGER', 'LONG', 'NUMBER'].includes(type)) {
    const numberValue = Number(value);
    if (!Number.isFinite(numberValue)) throw new Error('必须填写有效数字');
    return numberValue;
  }
  if (type === 'BOOLEAN') {
    if (!['false', 'true'].includes(value.toLowerCase()))
      throw new Error('布尔参数只能填写 true 或 false');
    return value.toLowerCase() === 'true';
  }
  return value;
}

async function handleRun() {
  if (!source.value || !draft.value) return;
  validationError.value = '';
  result.value = undefined;
  running.value = true;
  const startedAt = performance.now();
  try {
    const params: Record<string, unknown> = {};
    for (const field of parameters.value) {
      const value = coerceValue(parameterValues[field.name] ?? '', field.type);
      if (field.required && value === undefined)
        throw new Error(`参数 ${field.name} 为必填项`);
      if (value !== undefined) params[field.name] = value;
    }
    result.value = await trialRunDataSource({
      params,
      sourceId: source.value.id,
      versionId: draft.value.id,
    });
    message.success('试运行成功');
  } catch (error) {
    validationError.value = sanitizeTrialRunError(error);
  } finally {
    durationMs.value = Math.max(0, Math.round(performance.now() - startedAt));
    running.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  footer: false,
  async onOpenChange(open) {
    if (!open) {
      reset();
      return;
    }
    reset();
    const row = modalApi.getData<BpmFormDataSourceApi.Definition>();
    if (!row?.id) return;
    modalApi.lock();
    try {
      source.value = row;
      const versions = await getDataSourceVersionList(row.id);
      const latestDraft = versions.find((item) => item.status === 1);
      if (!latestDraft) {
        validationError.value = '当前没有可试运行的草稿，请先编辑并保存草稿';
        return;
      }
      draft.value = await getDataSourceVersion(row.id, latestDraft.id);
      for (const field of parameters.value) parameterValues[field.name] = '';
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="数据源试运行" class="w-3/4">
    <div class="max-h-[70vh] space-y-4 overflow-y-auto px-5 pb-5">
      <Alert
        message="试运行只执行已保存的草稿，不使用运行时缓存，也不会发布版本。服务端上下文参数不允许手工覆盖。"
        show-icon
        type="info"
      />
      <div v-if="parameters.length > 0" class="rounded border p-4">
        <h3 class="mb-3 font-medium">试运行参数</h3>
        <div
          v-for="field in parameters"
          :key="field.name"
          class="mb-3 grid grid-cols-[180px_1fr] items-center gap-3"
        >
          <label>
            {{ field.name }}（{{ field.type }}）
            <span v-if="field.required" class="text-red-500">*</span>
          </label>
          <Input
            v-model:value="parameterValues[field.name]"
            :placeholder="`请输入 ${field.name}`"
          />
        </div>
      </div>
      <div v-else class="text-sm text-gray-500">
        该草稿没有需要手工填写的参数。
      </div>
      <Button
        :disabled="!draft"
        :loading="running"
        type="primary"
        @click="handleRun"
      >
        开始试运行
      </Button>
      <Alert
        v-if="validationError"
        :message="validationError"
        show-icon
        type="error"
      />
      <Descriptions v-if="result" bordered size="small" :column="3">
        <DescriptionsItem label="返回数量">{{ result.total }}</DescriptionsItem>
        <DescriptionsItem label="草稿版本">
          V{{ result.version }}
        </DescriptionsItem>
        <DescriptionsItem label="请求耗时">
          {{ durationMs }} ms
        </DescriptionsItem>
      </Descriptions>
      <Table
        v-if="result"
        bordered
        :columns="columns"
        :data-source="result.rows"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 'max-content' }"
      />
    </div>
  </Modal>
</template>
