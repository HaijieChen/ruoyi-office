<script lang="ts">
import type { BpmFormDataSourceApi as MetadataCacheApi } from '#/api/bpm/form-data-source';

const metadataCache = new Map<
  string,
  MetadataCacheApi.PublishedMetadata
>();
</script>

<script lang="ts" setup>
import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';
import type { RemoteProps, SelectOption } from './remote-data-source-model';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Modal } from 'ant-design-vue';

import {
  getDataSourceSimpleList,
  getPublishedDataSourceMetadata,
} from '#/api/bpm/form-data-source';

import {
  buildBindingExpressionGroups,
  buildSchemaOptions,
  collectDesignerFormFields,
  getBindableParameters,
  reconcileRemoteProps,
  validateRemoteProps,
} from './remote-data-source-model';

const props = defineProps<{
  getActiveRule: () => Record<string, any> | undefined;
  getFormRules: () => unknown[];
  modelValue?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const INJECTED_PARAMETERS = new Set([
  'companyId',
  'deptId',
  'tenantId',
  'userId',
]);
const router = useRouter();
const revision = ref(0);
const loading = ref(false);
const error = ref('');
const sources = ref<BpmFormDataSourceApi.Definition[]>([]);
const metadata = ref<BpmFormDataSourceApi.PublishedMetadata>();

const activeRule = computed(() => {
  revision.value;
  return props.getActiveRule?.();
});
const activeProps = computed<RemoteProps>(() => activeRule.value?.props ?? {});
const formFields = computed(() =>
  collectDesignerFormFields(props.getFormRules?.() ?? []),
);
const sourceOptions = computed<SelectOption[]>(() => {
  const options = sources.value.map((source) => ({
    label: `${source.name}（${source.code} · V${source.publishedVersion}）`,
    value: source.code,
  }));
  const current = props.modelValue || activeProps.value.dataSourceCode;
  if (current && !options.some((item) => item.value === current)) {
    options.unshift({ label: `已停用或取消发布（${current}）`, value: current });
  }
  return options;
});
const resultOptions = computed(() =>
  metadata.value ? buildSchemaOptions(metadata.value.resultFields) : [],
);
const parameterOptions = computed(() =>
  metadata.value ? buildSchemaOptions(metadata.value.parameterFields) : [],
);
const targetOptions = computed<SelectOption[]>(() =>
  formFields.value.map((field) => ({
    label: `${field.title}（${field.field}）`,
    value: field.field,
  })),
);
const bindingGroups = computed(() =>
  buildBindingExpressionGroups(formFields.value),
);
const bindingOptions = computed(() =>
  bindingGroups.value.flatMap((group) => group.options),
);
const systemParameters = computed(() =>
  (metadata.value?.parameterFields ?? []).filter((field) =>
    INJECTED_PARAMETERS.has(field.name),
  ),
);
const bindableParameters = computed(() =>
  metadata.value
    ? getBindableParameters(metadata.value, activeProps.value)
    : [],
);
const parameterRows = computed(() =>
  Object.entries(activeProps.value.parameterBindings ?? {}).map(
    ([parameter, binding]) => ({ binding, parameter }),
  ),
);
const outputRows = computed(() =>
  Object.entries(activeProps.value.outputMappings ?? {}).map(
    ([result, target]) => ({ result, target }),
  ),
);
const issues = computed(() =>
  metadata.value
    ? validateRemoteProps(metadata.value, formFields.value, activeProps.value)
    : [],
);
const unavailable = computed(() => Boolean(error.value || !metadata.value));
const canAddParameter = computed(
  () =>
    !unavailable.value &&
    bindableParameters.value.some(
      (field) =>
        !Object.prototype.hasOwnProperty.call(
          activeProps.value.parameterBindings ?? {},
          field.name,
        ),
    ),
);
const canAddOutput = computed(
  () =>
    !unavailable.value &&
    metadata.value!.resultFields.some(
      (field) =>
        !Object.prototype.hasOwnProperty.call(
          activeProps.value.outputMappings ?? {},
          field.name,
        ),
    ) &&
    targetOptions.value.length > 0,
);

function replaceProps(next: RemoteProps) {
  const rule = props.getActiveRule?.();
  if (!rule) return;
  rule.props = { ...next };
  revision.value += 1;
}

function updateProps(patch: Partial<RemoteProps>) {
  replaceProps({ ...activeProps.value, ...patch });
}

function metadataCacheKey(code: string, publishedVersion?: number) {
  return `${code}:${publishedVersion ?? 'current'}`;
}

function clearCodeCache(code: string) {
  for (const key of metadataCache.keys()) {
    if (key.startsWith(`${code}:`)) metadataCache.delete(key);
  }
}

async function loadMetadata(code: string, force = false) {
  const source = sources.value.find((item) => item.code === code);
  if (!source?.publishedVersion) {
    throw new Error('所选数据源已停用或没有发布版本');
  }
  const key = metadataCacheKey(code, source.publishedVersion);
  if (!force && metadataCache.has(key)) return metadataCache.get(key)!;
  if (force) clearCodeCache(code);
  const detail = await getPublishedDataSourceMetadata(code);
  metadataCache.set(
    metadataCacheKey(code, detail.publishedVersion),
    detail,
  );
  return detail;
}

async function showMetadata(code: string, force = false) {
  loading.value = true;
  error.value = '';
  try {
    metadata.value = await loadMetadata(code, force);
    return metadata.value;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '元数据加载失败';
    if (!error.value.includes('元数据')) {
      error.value = `元数据加载失败：${error.value}`;
    }
    return undefined;
  } finally {
    loading.value = false;
  }
}

async function applySource(code: string) {
  const detail = await showMetadata(code);
  if (!detail) return;
  const next = reconcileRemoteProps(activeProps.value, detail);
  replaceProps(next);
  emit('update:modelValue', code);
}

function handleSourceChange(code: string) {
  if (!code || code === activeProps.value.dataSourceCode) return;
  if (!activeProps.value.dataSourceCode) {
    void applySource(code);
    return;
  }
  Modal.confirm({
    content: '将只保留新数据源中仍然有效且用途不冲突的配置。',
    okText: '确认切换',
    onOk: () => applySource(code),
    title: '切换数据源？',
  });
}

async function refresh() {
  const code = activeProps.value.dataSourceCode || props.modelValue;
  if (code) await showMetadata(code, true);
}

function optionWithHistoricalValue(
  options: SelectOption[],
  value: string | undefined,
): SelectOption[] {
  if (!value || options.some((item) => item.value === value)) return options;
  return [
    ...options,
    { disabled: true, label: `字段已不存在（${value}）`, value },
  ];
}

function disableUsedOptions(
  options: SelectOption[],
  used: string[],
  current: string | undefined,
) {
  const occupied = new Set(used.filter((value) => value !== current));
  return options.map((option) => ({
    ...option,
    disabled: option.disabled || occupied.has(option.value),
  }));
}

function parameterRowOptions(current: string) {
  return optionWithHistoricalValue(
    disableUsedOptions(
      buildSchemaOptions(bindableParameters.value),
      parameterRows.value.map((row) => row.parameter),
      current,
    ),
    current,
  );
}

function outputRowOptions(current: string) {
  return optionWithHistoricalValue(
    disableUsedOptions(
      resultOptions.value,
      outputRows.value.map((row) => row.result),
      current,
    ),
    current,
  );
}

function parameterPurposeOptions(current: string | undefined) {
  return optionWithHistoricalValue(
    disableUsedOptions(
      parameterOptions.value,
      [
        activeProps.value.searchParamName,
        activeProps.value.pageNoParamName,
        activeProps.value.pageSizeParamName,
      ].filter((value): value is string => Boolean(value)),
      current,
    ),
    current,
  );
}

function setParameterBinding(oldName: string, name: string) {
  const current = { ...(activeProps.value.parameterBindings ?? {}) };
  const binding = current[oldName] ?? bindingOptions.value[0]?.value ?? '';
  delete current[oldName];
  if (name) current[name] = binding;
  updateProps({ parameterBindings: current });
}

function setBindingExpression(parameter: string, binding: string) {
  updateProps({
    parameterBindings: {
      ...(activeProps.value.parameterBindings ?? {}),
      [parameter]: binding,
    },
  });
}

function addParameter() {
  const current = activeProps.value.parameterBindings ?? {};
  const parameter = bindableParameters.value.find(
    (field) => !Object.prototype.hasOwnProperty.call(current, field.name),
  );
  const binding = bindingOptions.value[0]?.value;
  if (parameter && binding) {
    updateProps({ parameterBindings: { ...current, [parameter.name]: binding } });
  }
}

function removeParameter(parameter: string) {
  const current = { ...(activeProps.value.parameterBindings ?? {}) };
  delete current[parameter];
  updateProps({ parameterBindings: current });
}

function setOutputResult(oldResult: string, result: string) {
  const current = { ...(activeProps.value.outputMappings ?? {}) };
  const target = current[oldResult] ?? targetOptions.value[0]?.value ?? '';
  delete current[oldResult];
  if (result) current[result] = target;
  updateProps({ outputMappings: current });
}

function setOutputTarget(result: string, target: string) {
  updateProps({
    outputMappings: {
      ...(activeProps.value.outputMappings ?? {}),
      [result]: target,
    },
  });
}

function addOutput() {
  const current = activeProps.value.outputMappings ?? {};
  const result = metadata.value?.resultFields.find(
    (field) => !Object.prototype.hasOwnProperty.call(current, field.name),
  );
  const target = targetOptions.value[0]?.value;
  if (result && target) {
    updateProps({ outputMappings: { ...current, [result.name]: target } });
  }
}

function removeOutput(result: string) {
  const current = { ...(activeProps.value.outputMappings ?? {}) };
  delete current[result];
  updateProps({ outputMappings: current });
}

onMounted(async () => {
  loading.value = true;
  try {
    sources.value = (await getDataSourceSimpleList()) ?? [];
  } catch {
    error.value = '数据源列表加载失败，请重试';
  } finally {
    loading.value = false;
  }
  const code = activeProps.value.dataSourceCode || props.modelValue;
  if (code) await showMetadata(code);
});
</script>

<template>
  <div class="space-y-3 py-1" :data-loading="loading">
    <div class="flex gap-2">
      <ASelect
        data-schema-select
        data-testid="source-select"
        data-next-value="crm_suppliers"
        class="min-w-0 flex-1"
        :model-value="activeProps.dataSourceCode || modelValue"
        :options="sourceOptions"
        placeholder="请选择已发布数据源"
        @change="handleSourceChange"
      />
      <AButton data-testid="refresh" :loading="loading" @click="refresh">
        刷新
      </AButton>
      <AButton data-testid="manage" @click="router.push({ name: 'BpmFormDataSource' })">
        管理数据源
      </AButton>
    </div>

    <AAlert v-if="error" type="error" show-icon>
      <template #message>{{ error }}</template>
      <template #action>
        <AButton data-testid="retry" size="small" @click="refresh">重试</AButton>
      </template>
    </AAlert>

    <div v-if="error && !metadata" class="space-y-2 rounded border p-2 text-xs">
      <div class="flex items-center justify-between">
        <span class="font-medium">请求参数绑定（保留原配置）</span>
        <AButton data-testid="add-parameter" disabled size="small">添加</AButton>
      </div>
      <div v-for="row in parameterRows" :key="row.parameter">
        {{ row.parameter }} → {{ row.binding }}
      </div>
      <div class="flex items-center justify-between">
        <span class="font-medium">结果字段联动（保留原配置）</span>
        <AButton data-testid="add-output" disabled size="small">添加</AButton>
      </div>
      <div v-for="row in outputRows" :key="row.result">
        {{ row.result }} → {{ row.target }}
      </div>
    </div>

    <template v-if="metadata">
      <section>
        <div class="mb-1 font-medium">系统自动注入</div>
        <div v-if="systemParameters.length" class="flex flex-wrap gap-1">
          <ATag v-for="field in systemParameters" :key="field.name">
            {{ field.label }}（{{ field.name }}）
          </ATag>
        </div>
        <div v-else class="text-xs text-gray-500">该数据源没有系统注入参数</div>
      </section>

      <section class="grid grid-cols-2 gap-2">
        <label>
          <span>显示字段</span>
          <ASelect
            data-schema-select
            :disabled="unavailable || !resultOptions.length"
            :model-value="activeProps.labelField"
            :options="optionWithHistoricalValue(resultOptions, activeProps.labelField)"
            @change="(value: string) => updateProps({ labelField: value })"
          />
        </label>
        <label>
          <span>值字段</span>
          <ASelect
            data-schema-select
            :disabled="unavailable || !resultOptions.length"
            :model-value="activeProps.valueField"
            :options="optionWithHistoricalValue(resultOptions, activeProps.valueField)"
            @change="(value: string) => updateProps({ valueField: value })"
          />
        </label>
      </section>
      <div v-if="!resultOptions.length" class="text-xs text-amber-600">
        数据源没有可用返回字段
      </div>

      <section>
        <div class="mb-1 flex items-center justify-between">
          <span class="font-medium">请求参数绑定</span>
          <AButton
            data-testid="add-parameter"
            :disabled="!canAddParameter"
            size="small"
            @click="addParameter"
          >添加</AButton>
        </div>
        <div v-for="row in parameterRows" :key="row.parameter" class="mb-1 flex gap-1">
          <ASelect
            data-schema-select
            class="flex-1"
            :model-value="row.parameter"
            :options="parameterRowOptions(row.parameter)"
            @change="(value: string) => setParameterBinding(row.parameter, value)"
          />
          <ASelect
            data-schema-select
            class="flex-1"
            :model-value="row.binding"
            :options="optionWithHistoricalValue(bindingOptions, row.binding)"
            @change="(value: string) => setBindingExpression(row.parameter, value)"
          />
          <AButton danger size="small" @click="removeParameter(row.parameter)">删除</AButton>
        </div>
        <div v-if="!bindableParameters.length" class="text-xs text-gray-500">
          没有需要手工绑定的参数
        </div>
      </section>

      <section>
        <div class="mb-1 flex items-center justify-between">
          <span class="font-medium">结果字段联动</span>
          <AButton
            data-testid="add-output"
            :disabled="!canAddOutput"
            size="small"
            @click="addOutput"
          >添加</AButton>
        </div>
        <div v-for="row in outputRows" :key="row.result" class="mb-1 flex gap-1">
          <ASelect
            data-schema-select
            class="flex-1"
            :model-value="row.result"
            :options="outputRowOptions(row.result)"
            @change="(value: string) => setOutputResult(row.result, value)"
          />
          <ASelect
            data-schema-select
            class="flex-1"
            :model-value="row.target"
            :options="optionWithHistoricalValue(targetOptions, row.target)"
            @change="(value: string) => setOutputTarget(row.result, value)"
          />
          <AButton danger size="small" @click="removeOutput(row.result)">删除</AButton>
        </div>
        <div v-if="!formFields.length" class="text-xs text-amber-600">
          当前表单没有可映射的普通字段
        </div>
      </section>

      <section class="grid grid-cols-2 gap-2">
        <label>
          <span>搜索参数</span>
          <ASelect
            data-schema-select
            allow-clear
            :model-value="activeProps.searchParamName"
            :options="parameterPurposeOptions(activeProps.searchParamName)"
            @change="(value?: string) => updateProps({ searchParamName: value })"
          />
        </label>
        <label>
          <span>依赖字段</span>
          <ASelect
            data-schema-select
            mode="multiple"
            :model-value="activeProps.dependencies || []"
            :options="targetOptions"
            @change="(value: string[]) => updateProps({ dependencies: value })"
          />
        </label>
        <label>
          <span>启用分页</span>
          <ASwitch
            :checked="activeProps.pageable"
            :disabled="!metadata.pageable"
            @change="(value: boolean) => updateProps({ pageable: value })"
          />
        </label>
        <template v-if="activeProps.pageable">
          <label>
            <span>页码参数</span>
            <ASelect
              data-schema-select
              :model-value="activeProps.pageNoParamName"
              :options="parameterPurposeOptions(activeProps.pageNoParamName)"
              @change="(value?: string) => updateProps({ pageNoParamName: value })"
            />
          </label>
          <label>
            <span>每页条数参数</span>
            <ASelect
              data-schema-select
              :model-value="activeProps.pageSizeParamName"
              :options="parameterPurposeOptions(activeProps.pageSizeParamName)"
              @change="(value?: string) => updateProps({ pageSizeParamName: value })"
            />
          </label>
          <label>
            <span>每页条数</span>
            <AInputNumber
              :max="200"
              :min="1"
              :value="activeProps.pageSize || 20"
              @change="(value: number) => updateProps({ pageSize: value })"
            />
          </label>
        </template>
        <label>
          <span>快照字段</span>
          <ASelect
            data-schema-select
            allow-clear
            :model-value="activeProps.snapshotField"
            :options="optionWithHistoricalValue(targetOptions, activeProps.snapshotField)"
            @change="(value?: string) => updateProps({ snapshotField: value })"
          />
        </label>
        <label>
          <span>依赖变更策略</span>
          <ASelect
            data-schema-select
            :model-value="activeProps.onDependencyChange || 'clear-and-reload'"
            :options="[
              { label: '清空并重新加载', value: 'clear-and-reload' },
              { label: '保留有效值并重新加载', value: 'keep-and-revalidate' },
            ]"
            @change="(value: string) => updateProps({ onDependencyChange: value })"
          />
        </label>
        <label>
          <span>是否多选</span>
          <ASwitch
            :checked="activeProps.multiple"
            @change="(value: boolean) => updateProps({ multiple: value })"
          />
        </label>
      </section>

      <div v-if="issues.length" class="space-y-1 text-xs text-red-500">
        <div v-for="item in issues" :key="`${item.code}:${item.field}`">
          {{ item.message }}
        </div>
      </div>
    </template>
  </div>
</template>
