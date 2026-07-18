import { describe, expect, it } from 'vitest';

import {
  buildVersionPayload,
  sanitizeTrialRunError,
  validateClientBindingKey,
  validatePlatformApiPath,
  validateSchemaFieldNames,
} from './data';

describe('form data source editor data', () => {
  it('normalizes and persists trimmed schema labels', () => {
    const payload = buildVersionPayload({
      config: {
        sql: 'SELECT id FROM demo WHERE tenant_id = :tenantId',
      },
      pageable: false,
      parameters: [
        {
          label: ' 当前租户 ',
          name: 'tenantId',
          required: true,
          type: 'long',
        },
      ],
      resultFields: [{ label: ' 编号 ', name: 'id', type: 'long' }],
      sourceType: 1,
    });

    expect(JSON.parse(payload.parameterSchema ?? '[]')[0].label).toBe(
      '当前租户',
    );
    expect(JSON.parse(payload.resultSchema ?? '[]')[0].label).toBe('编号');
  });

  it.each(['', '   ', '中'.repeat(65)])(
    'rejects invalid schema label %j',
    (label) => {
      expect(() =>
        buildVersionPayload({
          config: {
            sql: 'SELECT id FROM demo WHERE tenant_id = :tenantId',
          },
          pageable: false,
          parameters: [{ label, name: 'tenantId', type: 'LONG' }],
          resultFields: [{ label: '编号', name: 'id', type: 'LONG' }],
          sourceType: 1,
        }),
      ).toThrow(/中文名称/);
    },
  );

  it('normalizes version limits and serializes schemas', () => {
    const payload = buildVersionPayload({
      maxRows: 9999,
      parameters: [
        {
          label: '主体公司',
          name: 'companyId',
          required: true,
          type: 'LONG',
        },
      ],
      resultFields: [{ label: '编号', name: 'id', type: 'LONG' }],
      timeoutSeconds: 99,
    });

    expect(payload).toMatchObject({
      maxRows: 200,
      timeoutSeconds: 3,
    });
    expect(JSON.parse(payload.parameterSchema ?? '[]')).toEqual([
      {
        label: '主体公司',
        name: 'companyId',
        required: true,
        type: 'LONG',
      },
    ]);
    expect(JSON.parse(payload.resultSchema ?? '[]')).toEqual([
      { label: '编号', name: 'id', type: 'LONG' },
    ]);
  });

  it.each([
    'https://internal.example/admin-api/system/dept/simple-list',
    '//internal.example/admin-api/system/dept/simple-list',
    '/admin-api/system/../user/simple-list',
    '/admin-api/system/%2e%2e/user/simple-list',
    '/admin-api/system/dept/simple-list?tenantId=1',
  ])('rejects unsafe platform API path %s', (path) => {
    expect(() =>
      validatePlatformApiPath(path, ['/admin-api/system/dept/simple-list']),
    ).toThrow();
  });

  it('requires a platform API path to be explicitly allow-listed', () => {
    expect(
      validatePlatformApiPath('/admin-api/system/dept/simple-list', [
        '/admin-api/system/dept/simple-list',
      ]),
    ).toBe('/admin-api/system/dept/simple-list');
    expect(() =>
      validatePlatformApiPath('/admin-api/system/user/simple-list', [
        '/admin-api/system/dept/simple-list',
      ]),
    ).toThrow('未加入平台 API 白名单');
  });

  it('keeps server-owned context names out of browser parameter bindings', () => {
    expect(validateClientBindingKey('selectedCompanyId')).toBe(true);
    expect(validateClientBindingKey('tenantId')).toBe(false);
    expect(validateClientBindingKey('userId')).toBe(false);
    expect(validateClientBindingKey('deptId')).toBe(false);
    expect(validateClientBindingKey('companyId')).toBe(false);
    expect(validateClientBindingKey('constructor')).toBe(false);
  });

  it('uses the same safe schema field contract as persistence and linkage', () => {
    expect(() =>
      validateSchemaFieldNames([
        { label: '字段', name: 'a'.repeat(63), type: 'STRING' },
      ]),
    ).not.toThrow();
    for (const name of [
      'a'.repeat(64),
      'customer-id',
      '__proto__',
      'constructor',
      'prototype',
    ]) {
      expect(() =>
        validateSchemaFieldNames([{ label: '字段', name, type: 'STRING' }]),
      ).toThrow();
    }
  });

  it('does not surface stack traces from trial-run failures', () => {
    const error = {
      message:
        '查询失败\njava.lang.IllegalStateException: secret\n\tat com.example.DataSource.execute(DataSource.java:42)',
    };
    const message = sanitizeTrialRunError(error);

    expect(message).toBe('查询失败');
    expect(message).not.toContain('java.lang');
    expect(message).not.toContain('com.example');
  });
});
