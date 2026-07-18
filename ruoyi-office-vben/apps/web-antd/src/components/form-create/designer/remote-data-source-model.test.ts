import { describe, expect, it } from 'vitest';

import type { BpmFormDataSourceApi } from '#/api/bpm/form-data-source';

import {
  buildBindingExpressionGroups,
  buildSchemaOptions,
  collectDesignerFormFields,
  getBindableParameters,
  reconcileRemoteProps,
  validateRemoteProps,
} from './remote-data-source-model';

const metadata: BpmFormDataSourceApi.PublishedMetadata = {
  code: 'crm_customers',
  id: 1,
  labelField: 'name',
  name: '客商',
  pageable: true,
  parameterFields: [
    { label: '当前租户', name: 'tenantId', required: true, type: 'LONG' },
    { label: '关键词', name: 'keyword', type: 'STRING' },
    { label: '页码', name: 'pageNo', type: 'INTEGER' },
    { label: '每页条数', name: 'pageSize', type: 'INTEGER' },
    { label: '主体公司', name: 'company', required: true, type: 'LONG' },
  ],
  publishedVersion: 2,
  resultFields: [
    { label: '客商编号', name: 'id', required: true, type: 'LONG' },
    { label: '客商名称', name: 'name', type: 'STRING' },
    { label: '客商类型', name: 'typeName', type: 'STRING' },
  ],
  type: 1,
  valueField: 'id',
};

describe('remote data source designer model', () => {
  it('builds human-readable schema options with field type and required state', () => {
    expect(buildSchemaOptions(metadata.resultFields)).toEqual([
      { label: '客商编号（id · LONG · 必填）', value: 'id' },
      { label: '客商名称（name · STRING）', value: 'name' },
      { label: '客商类型（typeName · STRING）', value: 'typeName' },
    ]);
  });

  it('walks every designer rule container and excludes remote selectors', () => {
    const rules = [
      {
        children: [{ field: 'phone', title: '联系电话', type: 'input' }],
        control: [
          { rule: [{ field: 'email', title: '邮箱', type: 'input' }] },
        ],
        field: 'layout',
        props: {
          rule: [{ field: 'customerName', title: '客商名称', type: 'input' }],
        },
        title: '布局',
        type: 'group',
      },
      {
        props: {
          columns: [
            { rule: [{ field: 'taxNo', title: '纳税人识别号', type: 'input' }] },
          ],
        },
        type: 'tableForm',
      },
      {
        field: 'customerId',
        title: '客商',
        type: 'RemoteDataSourceSelect',
      },
    ];

    expect(
      collectDesignerFormFields(rules)
        .map((item) => item.field)
        .toSorted(),
    ).toEqual(['customerName', 'email', 'phone', 'taxNo']);
  });

  it('creates only safe FORM, USER and PROCESS binding expressions', () => {
    const fields = collectDesignerFormFields([
      { field: 'companyId', title: '主体公司', type: 'select' },
    ]);
    const groups = buildBindingExpressionGroups(fields);

    expect(groups.flatMap((group) => group.options.map((item) => item.value)))
      .toEqual([
        'FORM.companyId',
        'USER.id',
        'USER.deptId',
        'USER.companyId',
        'PROCESS.definitionKey',
        'PROCESS.instanceId',
      ]);
  });

  it('separates server-injected and special-purpose parameters from bindable parameters', () => {
    const props = {
      pageNoParamName: 'pageNo',
      pageSizeParamName: 'pageSize',
      pageable: true,
      searchParamName: 'keyword',
    };

    expect(getBindableParameters(metadata, props).map((item) => item.name))
      .toEqual(['company']);
  });

  it('reports stale fields and conflicting parameter purposes instead of hiding them', () => {
    const fields = collectDesignerFormFields([
      { field: 'companyId', title: '主体公司', type: 'select' },
      { field: 'customerName', title: '客商名称', type: 'input' },
    ]);
    const issues = validateRemoteProps(metadata, fields, {
      dataSourceCode: 'crm_customers',
      labelField: 'missingLabel',
      outputMappings: { missingResult: 'missingTarget' },
      pageNoParamName: 'keyword',
      parameterBindings: {
        company: 'USER.password',
        keyword: 'FORM.companyId',
      },
      pageable: true,
      searchParamName: 'keyword',
      valueField: 'id',
    });

    expect(issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'LABEL_FIELD_INVALID',
        'PARAMETER_PURPOSE_CONFLICT',
        'PARAMETER_BINDING_RESERVED',
        'BINDING_EXPRESSION_INVALID',
        'RESULT_FIELD_INVALID',
        'TARGET_FIELD_INVALID',
      ]),
    );
  });

  it('keeps only schema-compatible and non-conflicting props when switching sources', () => {
    const next: BpmFormDataSourceApi.PublishedMetadata = {
      ...metadata,
      code: 'crm_suppliers',
      labelField: 'name',
      parameterFields: [
        { label: '当前租户', name: 'tenantId', type: 'LONG' },
        { label: '关键词', name: 'keyword', type: 'STRING' },
        { label: '供应商分类', name: 'categoryId', type: 'LONG' },
      ],
      resultFields: [
        { label: '供应商编号', name: 'id', type: 'LONG' },
        { label: '供应商名称', name: 'name', type: 'STRING' },
      ],
      valueField: 'id',
    };

    expect(
      reconcileRemoteProps(
        {
          dataSourceCode: metadata.code,
          dependencies: ['companyId'],
          labelField: 'typeName',
          outputMappings: { name: 'customerName', typeName: 'customerType' },
          pageNoParamName: 'keyword',
          parameterBindings: {
            company: 'FORM.companyId',
            keyword: 'FORM.keyword',
          },
          pageable: true,
          searchParamName: 'keyword',
          snapshotField: 'customerSnapshot',
          valueField: 'id',
        },
        next,
      ),
    ).toMatchObject({
      dataSourceCode: 'crm_suppliers',
      dependencies: ['companyId'],
      labelField: 'name',
      outputMappings: { name: 'customerName' },
      parameterBindings: {},
      pageable: true,
      searchParamName: 'keyword',
      snapshotField: 'customerSnapshot',
      valueField: 'id',
    });
  });
});
