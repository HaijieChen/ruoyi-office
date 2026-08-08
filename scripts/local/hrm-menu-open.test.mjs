import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';

const repositoryRoot = new URL('../../', import.meta.url);

function readRepositoryFile(relativePath) {
  return readFileSync(new URL(relativePath, repositoryRoot), 'utf8');
}

test('HRM menu migration exposes implemented routes and full permissions', () => {
  const migrationPath = new URL('sql/mysql/hrm_menu_open.sql', repositoryRoot);
  assert.equal(existsSync(migrationPath), true, 'the HRM menu migration must be checked in');

  const migration = readFileSync(migrationPath, 'utf8');
  for (const component of [
    'hrm/employee/list/index',
    'hrm/employee/info/index',
    'hrm/employee-relation/entry/list/index',
    'hrm/employee-relation/entry/info/index',
    'hrm/employee-relation/regular/list/index',
    'hrm/employee-relation/regular/info/index',
    'hrm/employee-relation/resignation/list/index',
    'hrm/employee-relation/resignation/info/index',
    'hrm/employee-relation/transfer/list/index',
    'hrm/employee-relation/transfer/info/index',
  ]) {
    assert.ok(migration.includes(component), `missing HRM component: ${component}`);
  }

  for (const route of [
    '/hrm/employee/employee-archive-info',
    '/hrm/employee-relation/entry-info',
    '/hrm/employee-relation/regular-info',
    '/hrm/employee-relation/resignation-info',
    '/hrm/employee-relation/transfer-info',
  ]) {
    assert.ok(migration.includes(`'${route}'`), `missing absolute HRM detail route: ${route}`);
  }

  for (const permission of [
    'hrm:employee-archive:query',
    'hrm:employee-archive:create',
    'hrm:employee-archive:update',
    'hrm:employee-archive:delete',
    'hrm:employee-archive:export',
    'hrm:employee-entry-bill:query',
    'hrm:employee-entry-bill:create',
    'hrm:employee-entry-bill:update',
    'hrm:employee-entry-bill:delete',
    'hrm:employee-entry-bill:export',
    'hrm:employee-entry-bill:submit',
    'hrm:employee-entry-bill:withdraw',
    'hrm:employee-regular-bill:query',
    'hrm:employee-regular-bill:create',
    'hrm:employee-regular-bill:update',
    'hrm:employee-regular-bill:delete',
    'hrm:employee-regular-bill:export',
    'hrm:employee-regular-bill:submit',
    'hrm:employee-regular-bill:withdraw',
    'hrm:employee-resignation-bill:query',
    'hrm:employee-resignation-bill:create',
    'hrm:employee-resignation-bill:update',
    'hrm:employee-resignation-bill:delete',
    'hrm:employee-resignation-bill:export',
    'hrm:employee-resignation-bill:submit',
    'hrm:employee-resignation-bill:withdraw',
    'hrm:employee-transfer-bill:query',
    'hrm:employee-transfer-bill:create',
    'hrm:employee-transfer-bill:update',
    'hrm:employee-transfer-bill:delete',
    'hrm:employee-transfer-bill:export',
    'hrm:employee-transfer-bill:submit',
    'hrm:employee-transfer-bill:withdraw',
  ]) {
    assert.ok(migration.includes(permission), `missing HRM permission: ${permission}`);
  }

  assert.match(migration, /r\.`code`\s*=\s*'common'/, 'the common role must receive HRM access');

  const paymentDictionary = readRepositoryFile('sql/mysql/finance_payment_dict.sql');
  assert.match(paymentDictionary, /'薪资',\s*'SALARY',\s*'finance_payment_reason'/);
});
