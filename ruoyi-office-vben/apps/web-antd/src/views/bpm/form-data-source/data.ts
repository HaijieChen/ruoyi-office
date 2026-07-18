import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';

import { getRangePickerDefaultProps } from '#/utils';

export const MAX_ROWS = 200;
export const MAX_TIMEOUT_SECONDS = 3;
export const RESERVED_CONTEXT_PARAMETERS = new Set([
  'companyId',
  'deptId',
  'tenantId',
  'userId',
]);
const FORBIDDEN_SCHEMA_FIELD_NAMES = new Set([
  '__proto__',
  'constructor',
  'prototype',
]);

export const SOURCE_TYPE_OPTIONS = [
  { label: 'SQL 查询', value: 1 },
  { label: '数据字典', value: 2 },
  { label: '平台 API', value: 3 },
];

export const SCHEMA_TYPE_OPTIONS = [
  'STRING',
  'LONG',
  'INTEGER',
  'DECIMAL',
  'NUMBER',
  'BOOLEAN',
  'DATE',
  'DATETIME',
].map((value) => ({ label: value, value }));

export const MASK_OPTIONS = [
  { label: '不脱敏', value: '' },
  { label: '完全隐藏', value: 'FULL' },
  { label: '手机号', value: 'PHONE' },
  { label: '邮箱', value: 'EMAIL' },
  { label: '身份证', value: 'ID_CARD' },
  { label: '银行卡', value: 'BANK_CARD' },
];

export interface VersionEditorValues {
  cacheSeconds?: number;
  config: Partial<{
    dictType: string;
    method: 'GET';
    path: string;
    sql: string;
    userScoped: boolean;
  }>;
  labelField?: string;
  maxRows?: number;
  pageable: boolean;
  parameters: BpmFormDataSourceApi.SchemaField[];
  resultFields: BpmFormDataSourceApi.SchemaField[];
  sourceType: number;
  timeoutSeconds?: number;
  valueField?: string;
}

type VersionPayloadInput = Partial<VersionEditorValues> &
  Pick<VersionEditorValues, 'parameters' | 'resultFields'>;

function clampInteger(
  value: number | undefined,
  defaultValue: number,
  max: number,
) {
  const normalized = Number.isFinite(value)
    ? Math.trunc(value as number)
    : defaultValue;
  return Math.min(Math.max(normalized, 1), max);
}

function normalizeSchemaFields(fields: BpmFormDataSourceApi.SchemaField[]) {
  return fields.map(({ label, mask, name, required, type }) => ({
    ...(mask ? { mask } : {}),
    label: label.trim(),
    name: name.trim(),
    ...(required === undefined ? {} : { required }),
    type: type.toUpperCase(),
  }));
}

export function validateSchemaLabels(
  fields: BpmFormDataSourceApi.SchemaField[],
  section = 'Schema',
) {
  if (
    fields.some((field) => {
      const label = field.label?.trim();
      return !label || label.length > 64;
    })
  ) {
    throw new Error(`${section}字段中文名称必填，且长度不能超过 64 个字符`);
  }
}

export function validateSchemaFieldNames(
  fields: BpmFormDataSourceApi.SchemaField[],
  label = 'Schema',
) {
  const names = fields.map((field) => field.name.trim());
  if (
    names.some(
      (name) =>
        !/^[A-Z]\w{0,62}$/i.test(name) ||
        FORBIDDEN_SCHEMA_FIELD_NAMES.has(name),
    )
  ) {
    throw new Error(
      `${label}字段名须以字母开头，只能包含字母、数字和下划线，且长度不超过 63 个字符`,
    );
  }
  if (new Set(names).size !== names.length) {
    throw new Error(`${label}字段名不能重复`);
  }
  return names;
}

function buildSourceConfig(values: VersionPayloadInput) {
  const sourceType = values.sourceType ?? 1;
  if (sourceType === 1) {
    return {
      sql: String(values.config?.sql ?? '').trim(),
      userScoped: Boolean(values.config?.userScoped),
    };
  }
  if (sourceType === 2) {
    return { dictType: String(values.config?.dictType ?? '').trim() };
  }
  if (sourceType === 3) {
    return {
      method: 'GET',
      path: validatePlatformApiPath(
        String(values.config?.path ?? ''),
        readPlatformApiAllowedPaths(),
      ),
    };
  }
  throw new Error('不支持的数据源类型');
}

export function buildVersionPayload(
  values: VersionPayloadInput,
): BpmFormDataSourceApi.VersionSaveReq {
  validateSchemaFieldNames(values.parameters ?? [], '参数');
  validateSchemaFieldNames(values.resultFields ?? [], '结果');
  validateSchemaLabels(values.parameters ?? [], '参数');
  validateSchemaLabels(values.resultFields ?? [], '结果');
  const parameters = normalizeSchemaFields(values.parameters ?? []);
  const resultFields = normalizeSchemaFields(values.resultFields ?? []);
  const cacheSeconds = Number.isFinite(values.cacheSeconds)
    ? Math.max(0, Math.trunc(values.cacheSeconds as number))
    : 0;
  return {
    cacheSeconds,
    labelField: values.labelField?.trim() || undefined,
    maxRows: clampInteger(values.maxRows, MAX_ROWS, MAX_ROWS),
    pageable: Boolean(values.pageable),
    parameterSchema: JSON.stringify(parameters),
    resultSchema: JSON.stringify(resultFields),
    sourceConfig: JSON.stringify(buildSourceConfig(values)),
    timeoutSeconds: clampInteger(
      values.timeoutSeconds,
      MAX_TIMEOUT_SECONDS,
      MAX_TIMEOUT_SECONDS,
    ),
    valueField: values.valueField?.trim() || undefined,
  };
}

export function readPlatformApiAllowedPaths(): string[] {
  const configured = import.meta.env.VITE_BPM_FORM_API_ALLOWED_PATHS ?? '';
  return String(configured)
    .split(',')
    .map((path) => path.trim())
    .filter(Boolean);
}

export function validatePlatformApiPath(path: string, allowedPaths: string[]) {
  const normalized = path.trim();
  let decoded = normalized;
  try {
    decoded = decodeURIComponent(normalized);
  } catch {
    throw new Error('平台 API 路径编码不合法');
  }
  if (
    !/^\/[\w/-]+$/.test(normalized) ||
    normalized.startsWith('//') ||
    normalized.includes('..') ||
    decoded.includes('..') ||
    normalized.includes('\\')
  ) {
    throw new Error('平台 API 只能填写安全的相对路径');
  }
  if (!allowedPaths.includes(normalized)) {
    throw new Error('平台 API 路径未加入平台 API 白名单');
  }
  return normalized;
}

/** Browser linkage bindings may not overwrite context injected by the server. */
export function validateClientBindingKey(name: string) {
  return (
    /^[A-Z]\w{0,62}$/i.test(name) &&
    !FORBIDDEN_SCHEMA_FIELD_NAMES.has(name) &&
    !RESERVED_CONTEXT_PARAMETERS.has(name)
  );
}

export function sanitizeTrialRunError(error: unknown) {
  const candidate = error as {
    message?: unknown;
    response?: { data?: { message?: unknown; msg?: unknown } };
  };
  const raw =
    candidate?.response?.data?.msg ??
    candidate?.response?.data?.message ??
    candidate?.message;
  const firstLine = String(raw ?? '')
    .split(/\r?\n/)[0]
    ?.replace(/^Error:\s*/i, '')
    .trim();
  if (
    !firstLine ||
    /at\s+[\w.$]+\(|\bjava\.|exception\b|stack\s*trace/i.test(firstLine)
  ) {
    return '试运行失败，请检查数据源配置和参数';
  }
  return firstLine.slice(0, 160);
}

export function parseSchemaFields(
  value?: string,
): BpmFormDataSourceApi.SchemaField[] {
  if (!value) return [];
  try {
    const fields = JSON.parse(value);
    if (!Array.isArray(fields)) return [];
    return fields.map((field) => ({
      ...field,
      label: typeof field?.label === 'string' ? field.label : '',
    }));
  } catch {
    return [];
  }
}

export function parseSourceConfig(value?: string) {
  if (!value) return {};
  try {
    const config = JSON.parse(value);
    return config && typeof config === 'object' && !Array.isArray(config)
      ? config
      : {};
  } catch {
    return {};
  }
}

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入数据源名称' },
      fieldName: 'name',
      label: '名称',
    },
    {
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入数据源标识' },
      fieldName: 'code',
      label: '标识',
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: SOURCE_TYPE_OPTIONS,
        placeholder: '请选择数据源类型',
      },
      fieldName: 'type',
      label: '类型',
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '启用', value: 0 },
          { label: '停用', value: 1 },
        ],
        placeholder: '请选择状态',
      },
      fieldName: 'status',
      label: '状态',
    },
    {
      component: 'RangePicker',
      componentProps: { ...getRangePickerDefaultProps(), allowClear: true },
      fieldName: 'createTime',
      label: '创建时间',
    },
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'name', minWidth: 180, title: '名称' },
    { field: 'code', minWidth: 200, title: '标识' },
    {
      field: 'type',
      minWidth: 110,
      title: '类型',
      formatter: ({ cellValue }) =>
        SOURCE_TYPE_OPTIONS.find((item) => item.value === cellValue)?.label ??
        '-',
    },
    {
      field: 'status',
      minWidth: 90,
      title: '状态',
      formatter: ({ cellValue }) => (cellValue === 0 ? '启用' : '停用'),
    },
    {
      field: 'publishedVersion',
      minWidth: 110,
      title: '已发布版本',
      formatter: ({ cellValue }) =>
        cellValue === undefined || cellValue === null
          ? '未发布'
          : `V${cellValue}`,
    },
    {
      field: 'createTime',
      minWidth: 180,
      title: '创建时间',
      formatter: 'formatDateTime',
    },
    {
      fixed: 'right',
      slots: { default: 'actions' },
      title: '操作',
      width: 310,
    },
  ];
}
