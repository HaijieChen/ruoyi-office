import { describe, expect, it } from 'vitest';

import {
  buildVersionPayload,
  sanitizeTrialRunError,
  validateClientBindingKey,
  validatePlatformApiPath,
  validateSchemaFieldNames,
} from './data';

describe('form data source editor data', () => {
  it('normalizes version limits and serializes schemas', () => {
    const payload = buildVersionPayload({
      maxRows: 9999,
      parameters: [{ name: 'companyId', required: true, type: 'LONG' }],
      resultFields: [{ name: 'id', type: 'LONG' }],
      timeoutSeconds: 99,
    });

    expect(payload).toMatchObject({
      maxRows: 200,
      timeoutSeconds: 3,
    });
    expect(JSON.parse(payload.parameterSchema ?? '[]')).toEqual([
      { name: 'companyId', required: true, type: 'LONG' },
    ]);
    expect(JSON.parse(payload.resultSchema ?? '[]')).toEqual([
      { name: 'id', type: 'LONG' },
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
      validateSchemaFieldNames([{ name: 'a'.repeat(63), type: 'STRING' }]),
    ).not.toThrow();
    for (const name of [
      'a'.repeat(64),
      'customer-id',
      '__proto__',
      'constructor',
      'prototype',
    ]) {
      expect(() =>
        validateSchemaFieldNames([{ name, type: 'STRING' }]),
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
