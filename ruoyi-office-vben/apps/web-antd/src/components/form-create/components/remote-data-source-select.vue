<script lang="ts" setup>
import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';
import type { BindingContext } from '#/components/form-create/data-source/expression';
import type {
  RemoteDataSourceRuntimeContext,
  RemoteDataSourceSelectProps,
} from '#/components/form-create/typing';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { Select, SelectOption, Spin } from 'ant-design-vue';

import { executePublishedDataSource } from '#/api/bpm/form-data-source';
import {
  applyDependencyChange,
  readOwnPath,
} from '#/components/form-create/data-source/linkage';
import {
  buildExecuteParams,
  buildSelectionSnapshot,
  validateSelectorParamName,
} from '#/components/form-create/data-source/selector';

defineOptions({ name: 'RemoteDataSourceSelect' });

const props = withDefaults(defineProps<RemoteDataSourceSelectProps>(), {
  dependencies: () => [],
  labelField: 'name',
  multiple: false,
  onDependencyChange: 'clear-and-reload',
  outputMappings: () => ({}),
  pageable: false,
  pageSize: 20,
  pageNoParamName: undefined,
  pageSizeParamName: undefined,
  parameterBindings: () => ({}),
  runtimeContext: undefined,
  searchParamName: undefined,
  snapshotField: undefined,
  valueField: 'id',
});

const emit = defineEmits<{
  (e: 'selectedRecordChange', records: Record<string, unknown>[]): void;
  (e: 'update:modelValue', value: unknown): void;
}>();
const options = ref<Array<{ label: string; value: unknown }>>([]);
const rawRows = ref<Record<string, unknown>[]>([]);
const loading = ref(false);
const errorMessage = ref<string | undefined>();
const searchValue = ref('');
const currentPage = ref(1);
const total = ref(0);
let requestSequence = 0;
let searchTimer: ReturnType<typeof setTimeout> | undefined;

function isBoundedId(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= 64;
}

function isTrustedRuntimeContext(
  context: RemoteDataSourceRuntimeContext | undefined,
): context is RemoteDataSourceRuntimeContext {
  if (
    !context ||
    !Number.isSafeInteger(context.formId) ||
    context.formId <= 0
  ) {
    return false;
  }
  const hasDefinition = isBoundedId(context.processDefinitionId);
  const hasTask = isBoundedId(context.taskId);
  return hasDefinition !== hasTask;
}

const hasRuntimeContext = computed(() => {
  return isTrustedRuntimeContext(props.runtimeContext);
});

function hasDependencyValue(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return false;
  }
  return !Array.isArray(value) || value.length > 0;
}

const dependenciesReady = computed(() => {
  if (props.dependencies.length === 0) {
    return true;
  }
  const api = props.formCreateInject?.api;
  return (
    !!api &&
    props.dependencies.every((field) => hasDependencyValue(api.getValue(field)))
  );
});

function getConfigurationError(): string | undefined {
  if (!/^[a-z][a-z0-9_]{0,126}$/.test(props.dataSourceCode)) {
    return '数据源标识配置不正确';
  }

  try {
    readOwnPath({}, props.labelField);
    readOwnPath({}, props.valueField);
    for (const sourcePath of Object.keys(props.outputMappings)) {
      readOwnPath({}, sourcePath);
    }
  } catch {
    return '结果字段路径配置不正确';
  }

  const controlNames = new Set<string>();
  for (const paramName of Object.keys(props.parameterBindings)) {
    if (!validateSelectorParamName(paramName)) {
      return '请求参数名配置不正确';
    }
    controlNames.add(paramName);
  }

  if (props.searchParamName) {
    if (
      !validateSelectorParamName(props.searchParamName) ||
      controlNames.has(props.searchParamName)
    ) {
      return '搜索参数名配置不正确或重复';
    }
    controlNames.add(props.searchParamName);
  }

  if (!props.pageable) {
    if (props.pageNoParamName || props.pageSizeParamName) {
      return '未启用分页时不能配置分页参数';
    }
    return undefined;
  }

  if (
    !props.pageNoParamName ||
    !props.pageSizeParamName ||
    !validateSelectorParamName(props.pageNoParamName) ||
    !validateSelectorParamName(props.pageSizeParamName) ||
    controlNames.has(props.pageNoParamName) ||
    controlNames.has(props.pageSizeParamName) ||
    props.pageNoParamName === props.pageSizeParamName ||
    !Number.isInteger(props.pageSize) ||
    props.pageSize < 1 ||
    props.pageSize > 200
  ) {
    return '分页参数配置不正确或重复';
  }
  return undefined;
}

function buildBindingContext(): BindingContext {
  const userStore = useUserStore();
  const formValues: Record<string, unknown> =
    props.formCreateInject?.api?.formData?.() ?? {};
  const ctx = props.runtimeContext;
  return {
    FORM: formValues,
    PROCESS: {
      definitionKey: ctx?.processDefinitionKey,
      instanceId: ctx?.processInstanceId,
    },
    USER: {
      companyId: (userStore.userInfo as any)?.companyId,
      deptId: (userStore.userInfo as any)?.deptId,
      id: userStore.userInfo?.id,
    },
  };
}

function buildExecuteRequest(
  context: RemoteDataSourceRuntimeContext,
  params: Record<string, unknown>,
): BpmFormDataSourceApi.ExecuteReq {
  const base = { formId: context.formId, params };
  if (isBoundedId(context.processDefinitionId)) {
    return { ...base, processDefinitionId: context.processDefinitionId };
  }
  if (isBoundedId(context.taskId)) {
    return { ...base, taskId: context.taskId };
  }
  throw new Error('流程运行上下文不完整');
}

function setMappedValues(mappedValues: Record<string, unknown>) {
  const api = props.formCreateInject?.api;
  if (!api) return;
  for (const [targetField, value] of Object.entries(mappedValues)) {
    api.setValue(targetField, value);
  }
}

function findSelectedRows(
  value: unknown,
  rows: Record<string, unknown>[] = rawRows.value,
): Record<string, unknown>[] {
  let selectedValues: unknown[] = [];
  if (props.multiple) {
    selectedValues = Array.isArray(value) ? value : [];
  } else if (value !== undefined && value !== null) {
    selectedValues = [value];
  }
  return rows.filter((row) =>
    selectedValues.some((selectedValue) =>
      Object.is(readOwnPath(row, props.valueField), selectedValue),
    ),
  );
}

function writeSnapshot(selectedRows: Record<string, unknown>[]) {
  if (!props.snapshotField || !props.formCreateInject?.api) return;
  const snapshot = buildSelectionSnapshot(
    selectedRows,
    props.labelField,
    props.valueField,
  );
  props.formCreateInject.api.setValue(
    props.snapshotField,
    props.multiple ? snapshot : (snapshot[0] ?? null),
  );
}

function applySelection(value: unknown, emitValue = true) {
  if (emitValue) emit('update:modelValue', value);
  const selectedRows = findSelectedRows(value);
  emit('selectedRecordChange', selectedRows);

  const mappedValues: Record<string, unknown> = {};
  for (const [sourcePath, targetField] of Object.entries(
    props.outputMappings,
  )) {
    if (props.multiple) {
      mappedValues[targetField] = selectedRows.map((row) =>
        readOwnPath(row, sourcePath),
      );
    } else {
      mappedValues[targetField] = selectedRows[0]
        ? readOwnPath(selectedRows[0], sourcePath)
        : undefined;
    }
  }
  setMappedValues(mappedValues);
  writeSnapshot(selectedRows);
}

interface ExecuteOptions {
  append?: boolean;
  revalidateValue?: unknown;
}
let retryOptions: ExecuteOptions = {};

async function executeSource({
  append = false,
  revalidateValue,
}: ExecuteOptions = {}) {
  const requestId = ++requestSequence;
  const context = props.runtimeContext;
  if (!isTrustedRuntimeContext(context)) {
    return;
  }
  if (!dependenciesReady.value) {
    loading.value = false;
    errorMessage.value = undefined;
    options.value = [];
    rawRows.value = [];
    total.value = 0;
    retryOptions = {};
    return;
  }
  const configurationError = getConfigurationError();
  if (configurationError) {
    errorMessage.value = configurationError;
    return;
  }

  retryOptions = { append, revalidateValue };
  loading.value = true;
  errorMessage.value = undefined;
  try {
    const bindingContext = buildBindingContext();
    const params = buildExecuteParams(props.parameterBindings, bindingContext);
    // Configured control parameters are always sent, including an empty search,
    // so required published-schema parameters have deterministic semantics.
    if (props.searchParamName) {
      params[props.searchParamName] = searchValue.value;
    }
    if (props.pageable && props.pageNoParamName && props.pageSizeParamName) {
      params[props.pageNoParamName] = currentPage.value;
      params[props.pageSizeParamName] = props.pageSize;
    }
    const result = await executePublishedDataSource(
      props.dataSourceCode,
      buildExecuteRequest(context, params),
    );
    if (requestId !== requestSequence) return;

    const resultRows = result.rows ?? [];
    rawRows.value = append ? [...rawRows.value, ...resultRows] : resultRows;
    total.value = result.total ?? rawRows.value.length;
    options.value = rawRows.value.map((row) => ({
      label: String(readOwnPath(row, props.labelField) ?? ''),
      value: readOwnPath(row, props.valueField),
    }));

    if (revalidateValue !== undefined) {
      const change = applyDependencyChange({
        currentValue: revalidateValue,
        multiple: props.multiple,
        options: rawRows.value,
        outputMappings: props.outputMappings,
        strategy: 'keep-and-revalidate',
        valueField: props.valueField,
      });
      emit('update:modelValue', change.value);
      setMappedValues(change.mappedValues);
      const selectedRows = findSelectedRows(change.value);
      emit('selectedRecordChange', selectedRows);
      writeSnapshot(selectedRows);
    }
  } catch (error: any) {
    if (requestId !== requestSequence) return;
    errorMessage.value =
      error?.message?.split?.('\n')?.[0]?.slice(0, 160) ?? '加载数据源失败';
    if (!append) {
      options.value = [];
      rawRows.value = [];
      total.value = 0;
    }
  } finally {
    if (requestId === requestSequence) loading.value = false;
  }
}

function handleChange(newValue: unknown) {
  applySelection(newValue);
}

function handleSearch(value: string) {
  searchValue.value = value;
  if (!props.searchParamName) return;
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    searchTimer = undefined;
    currentPage.value = 1;
    executeSource();
  }, 300);
}

function clearSearchTimer() {
  if (!searchTimer) return;
  clearTimeout(searchTimer);
  searchTimer = undefined;
}

function handleRetry() {
  executeSource(retryOptions);
}

function handlePopupScroll(event: Event) {
  if (!props.pageable || loading.value || rawRows.value.length >= total.value) {
    return;
  }
  const target = event.target as HTMLElement | null;
  if (
    !target ||
    target.scrollTop + target.clientHeight < target.scrollHeight - 8
  ) {
    return;
  }
  currentPage.value += 1;
  executeSource({ append: true });
}

watch(
  [() => props.dataSourceCode, () => props.runtimeContext],
  () => {
    requestSequence += 1;
    clearSearchTimer();
    loading.value = false;
    currentPage.value = 1;
    searchValue.value = '';
    options.value = [];
    rawRows.value = [];
    total.value = 0;
    retryOptions = {};
    errorMessage.value = undefined;
    if (hasRuntimeContext.value) executeSource();
  },
  { deep: true, immediate: true },
);

watch(
  () => {
    const api = props.formCreateInject?.api;
    return (props.dependencies ?? []).map((field) => api?.getValue(field));
  },
  () => {
    if (!hasRuntimeContext.value || props.dependencies.length === 0) return;
    const currentValue = props.modelValue;
    if (props.onDependencyChange === 'clear-and-reload') {
      const change = applyDependencyChange({
        currentValue,
        multiple: props.multiple,
        outputMappings: props.outputMappings,
        strategy: 'clear-and-reload',
        valueField: props.valueField,
      });
      emit('update:modelValue', change.value);
      emit('selectedRecordChange', []);
      setMappedValues(change.mappedValues);
      writeSnapshot([]);
    }
    clearSearchTimer();
    currentPage.value = 1;
    searchValue.value = '';
    executeSource({
      revalidateValue:
        props.onDependencyChange === 'keep-and-revalidate'
          ? currentValue
          : undefined,
    });
  },
  { deep: true },
);

onBeforeUnmount(() => {
  requestSequence += 1;
  clearSearchTimer();
});

const snapshotOptions = computed(() => {
  if (hasRuntimeContext.value && options.value.length > 0) return undefined;
  if (props.snapshotField && props.formCreateInject?.api) {
    const snap = props.formCreateInject.api.getValue(props.snapshotField);
    if (snap) {
      const items = Array.isArray(snap) ? snap : [snap];
      return items
        .filter((s: any) => s && s.label !== undefined)
        .map((s: any) => ({ label: String(s.label), value: s.value }));
    }
  }
  return undefined;
});

const displayOptions = computed(() => snapshotOptions.value ?? options.value);
</script>

<template>
  <div class="remote-data-source-select w-full">
    <Spin v-if="loading && displayOptions.length === 0" size="small" />
    <Select
      v-else
      class="w-full"
      :disabled="
        (!hasRuntimeContext && displayOptions.length === 0) ||
        !dependenciesReady
      "
      :filter-option="false"
      :loading="loading"
      :mode="multiple ? 'multiple' : undefined"
      :not-found-content="
        errorMessage ? undefined : loading ? '加载中...' : '暂无数据'
      "
      :placeholder="
        !hasRuntimeContext
          ? '仅在流程中可用'
          : !dependenciesReady
            ? '请先完成前置选择'
            : errorMessage
              ? '加载失败'
              : '请选择'
      "
      :show-search="!!searchParamName"
      :value="modelValue as any"
      @change="handleChange"
      @popup-scroll="handlePopupScroll"
      @search="handleSearch"
    >
      <SelectOption
        v-for="(opt, idx) in displayOptions"
        :key="idx"
        :value="opt.value"
      >
        {{ opt.label }}
      </SelectOption>
    </Select>
    <div
      v-if="errorMessage && !loading"
      class="mt-1 cursor-pointer text-xs text-red-500"
      @click="handleRetry"
    >
      {{ errorMessage }}，点击重试
    </div>
  </div>
</template>
