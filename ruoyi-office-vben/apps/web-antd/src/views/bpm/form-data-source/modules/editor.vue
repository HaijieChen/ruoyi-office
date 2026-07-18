<script lang="ts" setup>
import type { FormInstance } from 'ant-design-vue';

import type { VersionEditorValues } from '../data';

import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';

import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Alert,
  Form as AntForm,
  Button,
  Checkbox,
  FormItem,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
  Textarea,
} from 'ant-design-vue';

import {
  createDataSourceWithDraft,
  getDataSource,
  getDataSourceVersion,
  getDataSourceVersionList,
  saveDataSourceDraft,
  updateDataSource,
} from '#/api/bpm/form-data-source';

import {
  buildVersionPayload,
  MASK_OPTIONS,
  parseSchemaFields,
  parseSourceConfig,
  readPlatformApiAllowedPaths,
  sanitizeTrialRunError,
  SCHEMA_TYPE_OPTIONS,
  SOURCE_TYPE_OPTIONS,
  validateSchemaFieldNames,
} from '../data';

const emit = defineEmits(['success']);

const definitionFormRef = ref<FormInstance>();
const editingId = ref<number>();
const definition = reactive<BpmFormDataSourceApi.DefinitionSaveReq>({
  code: '',
  name: '',
  type: 1,
});
const version = reactive<VersionEditorValues>({
  cacheSeconds: 0,
  config: { sql: '', userScoped: false },
  labelField: undefined,
  maxRows: 200,
  pageable: false,
  parameters: [],
  resultFields: [],
  sourceType: 1,
  timeoutSeconds: 3,
  valueField: undefined,
});

const title = computed(() =>
  editingId.value ? '编辑表单数据源' : '新增表单数据源',
);
const allowedApiPaths = computed(() => readPlatformApiAllowedPaths());

function resetEditor() {
  editingId.value = undefined;
  Object.assign(definition, { code: '', name: '', type: 1 });
  Object.assign(version, {
    cacheSeconds: 0,
    config: { sql: '', userScoped: false },
    labelField: undefined,
    maxRows: 200,
    pageable: false,
    parameters: [],
    resultFields: [],
    sourceType: 1,
    timeoutSeconds: 3,
    valueField: undefined,
  });
  definitionFormRef.value?.clearValidate();
}

function changeSourceType(value: unknown) {
  const type = Number(value);
  version.sourceType = type;
  if (type === 1) version.config = { sql: '', userScoped: false };
  if (type === 2) version.config = { dictType: '' };
  if (type === 3) version.config = { method: 'GET', path: '' };
}

function addParameter() {
  version.parameters.push({ name: '', required: false, type: 'STRING' });
}

function addResultField() {
  version.resultFields.push({ name: '', type: 'STRING' });
}

function validateSchemaRows() {
  for (const [label, fields] of [
    ['参数', version.parameters],
    ['结果', version.resultFields],
  ] as const) {
    validateSchemaFieldNames(fields, label);
  }
  if (definition.type === 1) {
    if (!String(version.config.sql ?? '').trim())
      throw new Error('请输入只读 SQL');
    if (!String(version.config.sql).includes(':tenantId')) {
      throw new Error('SQL 必须使用服务端参数 :tenantId 限制当前租户');
    }
    if (!version.parameters.some((field) => field.name === 'tenantId')) {
      throw new Error('参数 Schema 必须声明服务端参数 tenantId');
    }
  }
  if ([1, 3].includes(definition.type) && version.resultFields.length === 0) {
    throw new Error('SQL 查询和平台 API 至少需要一个结果字段');
  }
}

function applyVersion(detail: BpmFormDataSourceApi.Version) {
  Object.assign(version, {
    cacheSeconds: detail.cacheSeconds ?? 0,
    config: parseSourceConfig(detail.sourceConfig),
    labelField: detail.labelField,
    maxRows: detail.maxRows ?? 200,
    pageable: detail.pageable,
    parameters: parseSchemaFields(detail.parameterSchema),
    resultFields: parseSchemaFields(detail.resultSchema),
    sourceType: definition.type,
    timeoutSeconds: detail.timeoutSeconds ?? 3,
    valueField: detail.valueField,
  });
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    try {
      await definitionFormRef.value?.validate();
      validateSchemaRows();
      version.sourceType = definition.type;
      const payload = buildVersionPayload(version);
      modalApi.lock();
      if (editingId.value) {
        await updateDataSource({ ...definition, id: editingId.value });
        await saveDataSourceDraft(editingId.value, payload);
      } else {
        await createDataSourceWithDraft({ definition, version: payload });
      }
      await modalApi.close();
      emit('success');
      message.success('草稿已保存');
    } catch (error) {
      message.error(sanitizeTrialRunError(error));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) {
      resetEditor();
      return;
    }
    resetEditor();
    const row = modalApi.getData<BpmFormDataSourceApi.Definition>();
    if (!row?.id) return;
    modalApi.lock();
    try {
      const current = await getDataSource(row.id);
      editingId.value = current.id;
      Object.assign(definition, {
        code: current.code,
        id: current.id,
        name: current.name,
        type: current.type,
      });
      version.sourceType = current.type;
      const summaries = await getDataSourceVersionList(current.id);
      const selected =
        summaries.find((item) => item.status === 1) ?? summaries[0];
      if (selected)
        applyVersion(await getDataSourceVersion(current.id, selected.id));
      else changeSourceType(current.type);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="title" class="w-4/5">
    <div class="max-h-[70vh] space-y-5 overflow-y-auto px-5">
      <Alert
        message="安全说明"
        description="SQL 仅对具有编辑权限的管理员可见；运行页面和版本列表不会返回 SQL。tenantId、userId、deptId、companyId 由服务端注入，组件绑定请使用 selectedCompanyId 等业务参数名。"
        show-icon
        type="info"
      />
      <AntForm ref="definitionFormRef" :model="definition" layout="vertical">
        <div class="grid grid-cols-3 gap-4">
          <FormItem
            label="名称"
            name="name"
            :rules="[{ required: true, message: '请输入名称' }]"
          >
            <Input
              v-model:value="definition.name"
              :maxlength="63"
              placeholder="例如：可用印章"
            />
          </FormItem>
          <FormItem
            label="标识"
            name="code"
            :rules="[
              { required: true, message: '请输入标识' },
              {
                pattern: /^[a-z][a-z0-9_]*$/,
                message: '只能使用小写字母、数字和下划线',
              },
            ]"
          >
            <Input
              v-model:value="definition.code"
              :disabled="!!editingId"
              :maxlength="127"
              placeholder="oa_available_seals"
            />
          </FormItem>
          <FormItem label="类型" name="type" :rules="[{ required: true }]">
            <Select
              v-model:value="definition.type"
              :disabled="!!editingId"
              :options="SOURCE_TYPE_OPTIONS"
              @change="changeSourceType"
            />
          </FormItem>
        </div>
      </AntForm>

      <section class="rounded border p-4">
        <h3 class="mb-3 font-medium">数据源配置</h3>
        <template v-if="definition.type === 1">
          <Textarea
            v-model:value="version.config.sql"
            :auto-size="{ minRows: 6, maxRows: 14 }"
            placeholder="SELECT id, name FROM ... WHERE tenant_id = :tenantId"
          />
          <Checkbox v-model:checked="version.config.userScoped" class="mt-3">
            查询结果与当前用户相关（缓存按用户隔离）
          </Checkbox>
        </template>
        <template v-else-if="definition.type === 2">
          <Input
            v-model:value="version.config.dictType"
            placeholder="请输入字典类型，例如 common_status"
          />
        </template>
        <template v-else>
          <div class="grid grid-cols-[120px_1fr] gap-3">
            <Input value="GET" disabled />
            <Select
              v-model:value="version.config.path"
              :options="
                allowedApiPaths.map((path) => ({ label: path, value: path }))
              "
              placeholder="请选择平台管理员配置的白名单路径"
              show-search
            />
          </div>
          <Alert
            v-if="allowedApiPaths.length === 0"
            class="mt-3"
            message="当前前端未配置 VITE_BPM_FORM_API_ALLOWED_PATHS，暂不能保存平台 API 数据源。"
            type="warning"
          />
        </template>
      </section>

      <section class="rounded border p-4">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="font-medium">参数 Schema</h3>
          <Button type="dashed" @click="addParameter">添加参数</Button>
        </div>
        <div
          v-for="(field, index) in version.parameters"
          :key="index"
          class="mb-2 grid grid-cols-[1fr_180px_120px_60px] gap-2"
        >
          <Input v-model:value="field.name" placeholder="参数名" />
          <Select v-model:value="field.type" :options="SCHEMA_TYPE_OPTIONS" />
          <Checkbox v-model:checked="field.required">必填</Checkbox>
          <Button
            danger
            type="text"
            @click="version.parameters.splice(index, 1)"
          >
            删除
          </Button>
        </div>
        <div
          v-if="version.parameters.length === 0"
          class="text-sm text-gray-500"
        >
          暂无客户端或服务端参数
        </div>
      </section>

      <section class="rounded border p-4">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="font-medium">结果 Schema</h3>
          <Button type="dashed" @click="addResultField">添加字段</Button>
        </div>
        <div
          v-for="(field, index) in version.resultFields"
          :key="index"
          class="mb-2 grid grid-cols-[1fr_180px_180px_60px] gap-2"
        >
          <Input v-model:value="field.name" placeholder="字段名" />
          <Select v-model:value="field.type" :options="SCHEMA_TYPE_OPTIONS" />
          <Select
            v-model:value="field.mask"
            :options="MASK_OPTIONS"
            placeholder="脱敏策略"
          />
          <Button
            danger
            type="text"
            @click="version.resultFields.splice(index, 1)"
          >
            删除
          </Button>
        </div>
        <div
          v-if="version.resultFields.length === 0"
          class="text-sm text-gray-500"
        >
          暂无结果字段
        </div>
      </section>

      <section class="grid grid-cols-3 gap-4 rounded border p-4">
        <FormItem label="标签字段">
          <Select
            v-model:value="version.labelField"
            allow-clear
            :options="
              version.resultFields.map((field) => ({
                label: field.name,
                value: field.name,
              }))
            "
          />
        </FormItem>
        <FormItem label="值字段">
          <Select
            v-model:value="version.valueField"
            allow-clear
            :options="
              version.resultFields.map((field) => ({
                label: field.name,
                value: field.name,
              }))
            "
          />
        </FormItem>
        <FormItem label="支持分页">
          <Switch v-model:checked="version.pageable" />
        </FormItem>
        <FormItem label="最大返回行数（上限 200）">
          <InputNumber
            v-model:value="version.maxRows"
            :min="1"
            :max="200"
            class="w-full"
          />
        </FormItem>
        <FormItem label="超时秒数（上限 3）">
          <InputNumber
            v-model:value="version.timeoutSeconds"
            :min="1"
            :max="3"
            class="w-full"
          />
        </FormItem>
        <FormItem label="缓存秒数（0 为关闭）">
          <InputNumber
            v-model:value="version.cacheSeconds"
            :min="0"
            class="w-full"
          />
        </FormItem>
      </section>
    </div>
  </Modal>
</template>
