import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';

import { isBindingExpressionAllowed } from '../data-source/expression';

export interface SelectOption {
  disabled?: boolean;
  label: string;
  value: string;
}

export interface OptionGroup {
  label: string;
  options: SelectOption[];
}

export interface DesignerField {
  field: string;
  required: boolean;
  title: string;
  type: string;
}

export interface RemoteProps {
  dataSourceCode?: string;
  dependencies?: string[];
  labelField?: string;
  multiple?: boolean;
  onDependencyChange?: string;
  outputMappings?: Record<string, string>;
  pageNoParamName?: string;
  pageSize?: number;
  pageSizeParamName?: string;
  pageable?: boolean;
  parameterBindings?: Record<string, string>;
  searchParamName?: string;
  snapshotField?: string;
  valueField?: string;
  [key: string]: unknown;
}

export interface Issue {
  code: string;
  field?: string;
  message: string;
}

const SAFE_FIELD = /^[A-Za-z_][A-Za-z0-9_]*$/;
const INJECTED_PARAMETERS = new Set([
  'companyId',
  'deptId',
  'tenantId',
  'userId',
]);
const CONTAINER_TYPES = new Set([
  'col',
  'collapse',
  'div',
  'fcRow',
  'group',
  'row',
  'tableForm',
  'tabs',
]);

export function buildSchemaOptions(
  fields: BpmFormDataSourceApi.SchemaField[],
): SelectOption[] {
  return fields.map((field) => ({
    label: `${field.label}（${field.name} · ${field.type}${field.required ? ' · 必填' : ''}）`,
    value: field.name,
  }));
}

export function collectDesignerFormFields(rules: unknown[]): DesignerField[] {
  const result: DesignerField[] = [];
  const seen = new Set<string>();

  const walk = (items: unknown[]) => {
    for (const item of items) {
      if (!item || typeof item !== 'object') continue;
      const rule = item as Record<string, any>;
      const field = typeof rule.field === 'string' ? rule.field.trim() : '';
      const title = typeof rule.title === 'string' ? rule.title.trim() : '';
      const type = typeof rule.type === 'string' ? rule.type : '';
      if (
        field &&
        title &&
        SAFE_FIELD.test(field) &&
        type !== 'RemoteDataSourceSelect' &&
        !CONTAINER_TYPES.has(type) &&
        !seen.has(field)
      ) {
        seen.add(field);
        result.push({ field, required: Boolean(rule.$required), title, type });
      }

      if (Array.isArray(rule.children)) walk(rule.children);
      if (Array.isArray(rule.props?.rule)) walk(rule.props.rule);
      if (Array.isArray(rule.props?.columns)) {
        for (const column of rule.props.columns) {
          if (Array.isArray(column?.rule)) walk(column.rule);
        }
      }
      if (Array.isArray(rule.control)) {
        for (const control of rule.control) {
          if (Array.isArray(control?.rule)) walk(control.rule);
        }
      }
    }
  };

  walk(rules);
  return result;
}

export function buildBindingExpressionGroups(
  fields: DesignerField[],
): OptionGroup[] {
  return [
    {
      label: '当前表单',
      options: fields.map((field) => ({
        label: `${field.title}（${field.field}）`,
        value: `FORM.${field.field}`,
      })),
    },
    {
      label: '当前用户',
      options: [
        { label: '用户编号', value: 'USER.id' },
        { label: '部门编号', value: 'USER.deptId' },
        { label: '公司编号', value: 'USER.companyId' },
      ],
    },
    {
      label: '流程上下文',
      options: [
        { label: '流程定义标识', value: 'PROCESS.definitionKey' },
        { label: '流程实例编号', value: 'PROCESS.instanceId' },
      ],
    },
  ];
}

function specialParameterNames(props: RemoteProps): string[] {
  return [
    props.searchParamName,
    props.pageable ? props.pageNoParamName : undefined,
    props.pageable ? props.pageSizeParamName : undefined,
  ].filter((name): name is string => Boolean(name));
}

export function getBindableParameters(
  metadata: BpmFormDataSourceApi.PublishedMetadata,
  props: RemoteProps,
): BpmFormDataSourceApi.SchemaField[] {
  const reserved = new Set(specialParameterNames(props));
  return metadata.parameterFields.filter(
    (field) => !INJECTED_PARAMETERS.has(field.name) && !reserved.has(field.name),
  );
}

function issue(code: string, message: string, field?: string): Issue {
  return { code, field, message };
}

export function validateRemoteProps(
  metadata: BpmFormDataSourceApi.PublishedMetadata,
  fields: DesignerField[],
  props: RemoteProps,
): Issue[] {
  const issues: Issue[] = [];
  const parameterNames = new Set(
    metadata.parameterFields.map((field) => field.name),
  );
  const resultNames = new Set(metadata.resultFields.map((field) => field.name));
  const targetNames = new Set(fields.map((field) => field.field));

  if (props.dataSourceCode && props.dataSourceCode !== metadata.code) {
    issues.push(issue('DATA_SOURCE_MISMATCH', '当前配置与所选数据源不一致'));
  }
  if (!props.labelField || !resultNames.has(props.labelField)) {
    issues.push(issue('LABEL_FIELD_INVALID', '显示字段不在数据源返回字段中', 'labelField'));
  }
  if (!props.valueField || !resultNames.has(props.valueField)) {
    issues.push(issue('VALUE_FIELD_INVALID', '值字段不在数据源返回字段中', 'valueField'));
  }

  const specialNames = specialParameterNames(props);
  const occupied = new Set<string>();
  for (const name of specialNames) {
    if (!parameterNames.has(name)) {
      issues.push(issue('SPECIAL_PARAMETER_INVALID', `参数 ${name} 不在数据源 Schema 中`, name));
    }
    if (occupied.has(name)) {
      issues.push(issue('PARAMETER_PURPOSE_CONFLICT', `参数 ${name} 被多个用途重复占用`, name));
    }
    occupied.add(name);
  }
  if (props.pageable && !metadata.pageable) {
    issues.push(issue('PAGEABLE_NOT_SUPPORTED', '该数据源未启用分页', 'pageable'));
  }

  const allowedExpressions = new Set(
    buildBindingExpressionGroups(fields).flatMap((group) =>
      group.options.map((option) => option.value),
    ),
  );
  for (const [parameter, binding] of Object.entries(
    props.parameterBindings ?? {},
  )) {
    if (!parameterNames.has(parameter)) {
      issues.push(issue('PARAMETER_FIELD_INVALID', `参数 ${parameter} 不在数据源 Schema 中`, parameter));
    }
    if (INJECTED_PARAMETERS.has(parameter) || occupied.has(parameter)) {
      issues.push(issue('PARAMETER_BINDING_RESERVED', `参数 ${parameter} 由系统或专用配置提供`, parameter));
    }
    if (
      !isBindingExpressionAllowed(binding) ||
      !allowedExpressions.has(binding)
    ) {
      issues.push(issue('BINDING_EXPRESSION_INVALID', `绑定表达式 ${binding} 不可用`, parameter));
    }
  }

  const bindingNames = new Set(Object.keys(props.parameterBindings ?? {}));
  for (const parameter of getBindableParameters(metadata, props)) {
    if (parameter.required && !bindingNames.has(parameter.name)) {
      issues.push(issue('REQUIRED_PARAMETER_UNBOUND', `必填参数 ${parameter.label} 尚未绑定`, parameter.name));
    }
  }

  for (const [result, target] of Object.entries(props.outputMappings ?? {})) {
    if (!resultNames.has(result)) {
      issues.push(issue('RESULT_FIELD_INVALID', `结果字段 ${result} 不在数据源 Schema 中`, result));
    }
    if (!targetNames.has(target)) {
      issues.push(issue('TARGET_FIELD_INVALID', `目标字段 ${target} 不在当前表单中`, target));
    }
  }
  for (const dependency of props.dependencies ?? []) {
    if (!targetNames.has(dependency)) {
      issues.push(issue('DEPENDENCY_FIELD_INVALID', `依赖字段 ${dependency} 不在当前表单中`, dependency));
    }
  }
  if (props.snapshotField && !targetNames.has(props.snapshotField)) {
    issues.push(issue('SNAPSHOT_FIELD_INVALID', '快照字段不在当前表单中', props.snapshotField));
  }
  return issues;
}

export function reconcileRemoteProps(
  previous: RemoteProps,
  next: BpmFormDataSourceApi.PublishedMetadata,
): RemoteProps {
  const parameterNames = new Set(next.parameterFields.map((field) => field.name));
  const resultNames = new Set(next.resultFields.map((field) => field.name));
  const reconciled: RemoteProps = { ...previous, dataSourceCode: next.code };

  const used = new Set<string>();
  const keepSpecial = (key: 'pageNoParamName' | 'pageSizeParamName' | 'searchParamName') => {
    const name = previous[key];
    if (name && parameterNames.has(name) && !used.has(name)) {
      reconciled[key] = name;
      used.add(name);
    } else {
      delete reconciled[key];
    }
  };
  keepSpecial('searchParamName');
  if (previous.pageable && next.pageable) {
    keepSpecial('pageNoParamName');
    keepSpecial('pageSizeParamName');
  } else {
    delete reconciled.pageNoParamName;
    delete reconciled.pageSizeParamName;
  }

  reconciled.pageable = Boolean(previous.pageable && next.pageable);
  reconciled.labelField =
    previous.labelField && resultNames.has(previous.labelField)
      ? previous.labelField
      : next.labelField;
  reconciled.valueField =
    previous.valueField && resultNames.has(previous.valueField)
      ? previous.valueField
      : next.valueField;
  reconciled.parameterBindings = Object.fromEntries(
    Object.entries(previous.parameterBindings ?? {}).filter(
      ([parameter]) =>
        parameterNames.has(parameter) &&
        !INJECTED_PARAMETERS.has(parameter) &&
        !used.has(parameter),
    ),
  );
  reconciled.outputMappings = Object.fromEntries(
    Object.entries(previous.outputMappings ?? {}).filter(([result]) =>
      resultNames.has(result),
    ),
  );
  return reconciled;
}
