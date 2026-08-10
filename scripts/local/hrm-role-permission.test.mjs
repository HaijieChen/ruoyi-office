import assert from 'node:assert/strict';
import { spawnSync, execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test, { after, before } from 'node:test';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const mysqlContainer = process.env.HRM_TEST_MYSQL_CONTAINER ?? 'ruoyi-office-mysql';
const mysqlUser = process.env.HRM_TEST_MYSQL_USER ?? 'root';
const apiBaseUrl = process.env.HRM_TEST_API_BASE_URL ?? 'http://127.0.0.1:48080/admin-api';
const tenantId = '1';

const bpmMenuIds = [1185, 1200, 1201, 1202, 1207, 1208, 1219, 1220, 1221, 1222, 2713, 2720];
const hrmMenuIds = [
  5152, 5154, 5155, 5156, 5157, 5158, 5159,
  ...Array.from({ length: 36 }, (_, index) => 5161 + index),
  5256, 5257, 5258,
];
const organizationMenuIds = [5148, 5149, 5150];
// 系统管理侧按钮（父链 103→1，get-permission-info 会被 filterDisableMenus 丢掉）
const systemDeptPermissionMenuIds = [1017, 1018, 1019, 1020];
// 人力 → 组织管理 下挂的同权按钮（父链完整，前端 auth 可见）
const hrmDeptPermissionMenuIds = [5259, 5260, 5261, 5262];
const deptPermissionMenuIds = [...systemDeptPermissionMenuIds, ...hrmDeptPermissionMenuIds];
const expectedMenuIds = [
  ...bpmMenuIds,
  5200,
  5255,
  ...hrmMenuIds,
  ...organizationMenuIds,
  ...deptPermissionMenuIds,
].sort((left, right) => left - right);

const bpmPermissions = [
  'bpm:process-instance:query',
  'bpm:process-instance:create',
  'bpm:process-instance:cancel',
  'bpm:process-instance-cc:query',
  'bpm:task:query',
  'bpm:task:update',
];

const hrmPermissions = [
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
];

const organizationPermissions = [
  'system:dept:query',
  'system:dept:create',
  'system:dept:update',
  'system:dept:delete',
];

const expectedPermissions = [...bpmPermissions, ...hrmPermissions, ...organizationPermissions].sort();

let mysqlPassword;
let sourceDatabase;
let isolatedDatabase;
let apiToken;

const menuTreeSnapshotSql = `
  SELECT id, COALESCE(name, ''), COALESCE(permission, ''), type, sort, parent_id,
         COALESCE(path, ''), COALESCE(component, ''), COALESCE(component_name, ''),
         status, visible, keep_alive, always_show, deleted
  FROM system_menu
  ORDER BY id;
`;

function readRepositoryFile(relativePath) {
  return readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
}

function dockerEnvValue(key) {
  const result = execFileSync(
    'docker',
    ['inspect', mysqlContainer, '--format', '{{range .Config.Env}}{{println .}}{{end}}'],
    { encoding: 'utf8' },
  );
  return result
    .split('\n')
    .find((line) => line.startsWith(`${key}=`))
    ?.slice(key.length + 1);
}

function invokeDocker(command, args, input, encoding = 'utf8') {
  return spawnSync(
    'docker',
    ['exec', '-i', '-e', `MYSQL_PWD=${mysqlPassword}`, mysqlContainer, command, ...args],
    { input, encoding, maxBuffer: 128 * 1024 * 1024 },
  );
}

function mysqlArguments(database) {
  const args = [
    '--default-character-set=utf8mb4',
    '--batch',
    '--skip-column-names',
    '--raw',
    '--user',
    mysqlUser,
  ];
  if (database) {
    args.push(database);
  }
  return args;
}

function runMysql(database, sql, { allowFailure = false } = {}) {
  const result = invokeDocker('mysql', mysqlArguments(database), sql);
  if (!allowFailure && result.status !== 0) {
    throw new Error(`mysql failed (${result.status}): ${result.stderr || result.stdout}`);
  }
  return result;
}

function runMysqlFile(database, relativePath, options) {
  return runMysql(database, readRepositoryFile(relativePath), options);
}

function queryRows(database, sql) {
  const result = runMysql(database, sql);
  const output = result.stdout.trim();
  return output ? output.split('\n').map((line) => line.split('\t')) : [];
}

function queryColumn(database, sql) {
  return queryRows(database, sql).map(([value]) => value);
}

function queryScalar(database, sql) {
  const [value] = queryColumn(database, sql);
  return value;
}

function cloneSourceDatabase() {
  const createResult = runMysql(null, `CREATE DATABASE \`${isolatedDatabase}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`);
  assert.equal(createResult.status, 0, 'the isolated database must be created');

  const dumpResult = invokeDocker(
    'mysqldump',
    [
      '--user',
      mysqlUser,
      '--single-transaction',
      '--quick',
      '--routines',
      '--triggers',
      '--no-create-db',
      '--set-gtid-purged=OFF',
      sourceDatabase,
    ],
    undefined,
    null,
  );
  assert.equal(dumpResult.status, 0, `source database dump failed: ${dumpResult.stderr}`);

  const importResult = invokeDocker(
    'mysql',
    mysqlArguments(isolatedDatabase),
    dumpResult.stdout,
    null,
  );
  assert.equal(importResult.status, 0, `isolated database import failed: ${importResult.stderr}`);
}

async function apiRequest(method, requestPath, body) {
  const headers = { 'tenant-id': tenantId };
  if (apiToken) {
    headers.authorization = `Bearer ${apiToken}`;
  }
  if (body !== undefined) {
    headers['content-type'] = 'application/json';
  }
  const response = await fetch(`${apiBaseUrl}${requestPath}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const payload = await response.json();
  return { httpStatus: response.status, payload };
}

before(async () => {
  mysqlPassword = process.env.HRM_TEST_MYSQL_PASSWORD ?? dockerEnvValue('MYSQL_ROOT_PASSWORD');
  sourceDatabase = process.env.HRM_TEST_MYSQL_DATABASE ?? dockerEnvValue('MYSQL_DATABASE') ?? 'ruoyi-office';
  assert.ok(mysqlPassword, 'set HRM_TEST_MYSQL_PASSWORD or expose MYSQL_ROOT_PASSWORD in the MySQL container');

  isolatedDatabase = `hrm_role_contract_${process.pid}_${Date.now()}`;
  cloneSourceDatabase();

  runMysql(isolatedDatabase, `
    UPDATE system_user_role ur
    INNER JOIN system_users u ON u.id = ur.user_id
    SET ur.deleted = b'1'
    WHERE u.username = 'hradminuser' AND u.tenant_id = 1;
    UPDATE system_role_menu rm
    INNER JOIN system_role r ON r.id = rm.role_id
    SET rm.deleted = b'1'
    WHERE r.code = 'hr_admin' AND r.tenant_id = 1;
    UPDATE system_users
    SET deleted = b'1'
    WHERE username = 'hradminuser' AND tenant_id = 1;
    UPDATE system_role
    SET deleted = b'1'
    WHERE code = 'hr_admin' AND tenant_id = 1;
  `);

  runMysqlFile(isolatedDatabase, 'sql/mysql/workbench_role_menu_home.sql');
  runMysqlFile(isolatedDatabase, 'sql/mysql/hrm_menu_open.sql');
  const menuTreeBeforeRoleSeed = runMysql(isolatedDatabase, menuTreeSnapshotSql).stdout;
  runMysqlFile(isolatedDatabase, 'sql/mysql/hrm_roles_hr_admin.sql');
  const menuTreeAfterRoleSeed = runMysql(isolatedDatabase, menuTreeSnapshotSql).stdout;
  assert.equal(menuTreeAfterRoleSeed, menuTreeBeforeRoleSeed, 'role seed must not mutate system_menu');
  runMysqlFile(isolatedDatabase, 'sql/mysql/hrm_test_user_hr_admin.sql');

  const login = await apiRequest('POST', '/system/auth/login', {
    username: 'hradminuser',
    password: 'admin123',
  });
  assert.equal(login.payload.code, 0, `local API login failed: ${JSON.stringify(login.payload)}`);
  apiToken = login.payload.data.accessToken;
});

after(() => {
  if (isolatedDatabase) {
    runMysql(null, `DROP DATABASE IF EXISTS \`${isolatedDatabase}\`;`);
  }
});

test('model list and sort endpoints enforce their distinct permissions', () => {
  const controller = readRepositoryFile(
    'yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/BpmModelController.java',
  );
  const listMethod = controller.slice(controller.indexOf('@GetMapping("/list")'), controller.indexOf('@GetMapping("/get")'));
  const sortMethod = controller.slice(controller.indexOf('@PutMapping("/update-sort-batch")'), controller.indexOf('@PostMapping("/deploy")'));

  assert.match(listMethod, /@PreAuthorize\("@ss\.hasPermission\('bpm:model:query'\)"\)/);
  assert.match(sortMethod, /@PreAuthorize\("@ss\.hasPermission\('bpm:model:update'\)"\)/);
});

test('legacy workspace landing route redirects to the canonical home route', () => {
  const coreRoutes = readRepositoryFile('ruoyi-office-vben/apps/web-antd/src/router/routes/core.ts');
  const preferences = readRepositoryFile('ruoyi-office-vben/apps/web-antd/src/preferences.ts');

  assert.match(preferences, /defaultHomePath:\s*'\/home'/);
  assert.match(
    coreRoutes,
    /name:\s*'WorkspaceCompatibility'[\s\S]*?path:\s*'\/workspace'[\s\S]*?redirect:\s*preferences\.app\.defaultHomePath/,
  );
  assert.match(
    coreRoutes,
    /name:\s*'WorkspaceCompatibility'[\s\S]*?hideInMenu:\s*true/,
  );
});

test('isolated role seed converges to the exact menu and permission contract', () => {
  const roleSql = readRepositoryFile('sql/mysql/hrm_roles_hr_admin.sql');
  const accountSql = readRepositoryFile('sql/mysql/hrm_test_user_hr_admin.sql');
  assert.doesNotMatch(roleSql, /UPDATE\s+`system_menu`/i, 'role seed must not mutate the global menu tree');
  assert.match(roleSql, /SIGNAL\s+SQLSTATE\s+'45000'/i, 'selector failures must use SIGNAL');
  for (const permission of organizationPermissions) {
    assert.match(roleSql, new RegExp(permission.replaceAll(':', '\\:')));
  }
  assert.match(accountSql, /SIGNAL\s+SQLSTATE\s+'45000'/i, 'account selector failures must use SIGNAL');
  assert.doesNotMatch(accountSql, /ORDER BY\s+`id`\s+LIMIT\s+1/i, 'account selectors must not silently choose the first duplicate');

  const menuIds = queryColumn(isolatedDatabase, `
    SELECT m.id
    FROM system_role_menu rm
    INNER JOIN system_role r ON r.id = rm.role_id
    INNER JOIN system_menu m ON m.id = rm.menu_id
    WHERE r.code = 'hr_admin' AND r.tenant_id = 1
      AND rm.tenant_id = 1 AND rm.deleted = b'0' AND m.deleted = b'0'
    ORDER BY m.id;
  `).map(Number);
  assert.deepEqual(menuIds, expectedMenuIds);

  const hrmRootId = Number(queryScalar(isolatedDatabase, `
    SELECT id
    FROM system_menu
    WHERE deleted = b'0' AND parent_id = 0 AND path = '/hrm';
  `));
  const organizationParents = queryRows(isolatedDatabase, `
    SELECT id, parent_id
    FROM system_menu
    WHERE id IN (${organizationMenuIds.join(',')})
    ORDER BY id;
  `).map(([id, parentId]) => [Number(id), Number(parentId)]);
  assert.deepEqual(organizationParents, [
    [organizationMenuIds[0], hrmRootId],
    [organizationMenuIds[1], organizationMenuIds[0]],
    [organizationMenuIds[2], organizationMenuIds[0]],
  ]);

  const permissions = queryColumn(isolatedDatabase, `
    SELECT DISTINCT m.permission
    FROM system_role_menu rm
    INNER JOIN system_role r ON r.id = rm.role_id
    INNER JOIN system_menu m ON m.id = rm.menu_id
    WHERE r.code = 'hr_admin' AND r.tenant_id = 1
      AND rm.tenant_id = 1 AND rm.deleted = b'0' AND m.deleted = b'0'
      AND m.permission <> ''
    ORDER BY m.permission;
  `);
  assert.deepEqual(permissions, expectedPermissions);

  const roleCodes = queryColumn(isolatedDatabase, `
    SELECT DISTINCT r.code
    FROM system_user_role ur
    INNER JOIN system_users u ON u.id = ur.user_id
    INNER JOIN system_role r ON r.id = ur.role_id
    WHERE u.username = 'hradminuser' AND u.tenant_id = 1
      AND ur.tenant_id = 1 AND ur.deleted = b'0'
      AND u.deleted = b'0' AND r.deleted = b'0'
    ORDER BY r.code;
  `);
  assert.deepEqual(roleCodes, ['hr_admin']);

  const forbiddenSelectors = {
    system: `(m.path LIKE '/system%' OR m.component LIKE 'system/%' OR m.permission LIKE 'system:%') AND NOT (m.permission LIKE 'system:dept:%' OR (m.name = '组织管理' AND m.component = 'system/dept/index' AND m.parent_id = ${organizationMenuIds[0]}) OR (m.name = '组织架构图' AND m.component = 'system/dept/org-chart' AND m.parent_id = ${organizationMenuIds[0]}))`,
    model: "m.path = 'model' OR m.component LIKE 'bpm/model/%' OR m.permission LIKE 'bpm:model:%'",
    form: "m.path IN ('form', 'form-data-source') OR m.component LIKE 'bpm/form/%' OR m.permission LIKE 'bpm:form:%'",
    organization: `m.path = 'dept' OR (m.component = 'system/dept/index' AND m.parent_id <> ${organizationMenuIds[0]}) OR m.name = '组织架构管理'`,
    attendance: "m.path LIKE '%attend%' OR m.path LIKE '%leave%' OR m.component LIKE '%attend%' OR m.component LIKE '%leave%' OR m.permission LIKE 'bpm:oa-leave:%' OR m.name LIKE '%考勤%' OR m.name LIKE '%请假%'",
  };
  for (const [category, selector] of Object.entries(forbiddenSelectors)) {
    const count = Number(queryScalar(isolatedDatabase, `
      SELECT COUNT(*)
      FROM system_role_menu rm
      INNER JOIN system_role r ON r.id = rm.role_id
      INNER JOIN system_menu m ON m.id = rm.menu_id
      WHERE r.code = 'hr_admin' AND r.tenant_id = 1
        AND rm.tenant_id = 1 AND rm.deleted = b'0' AND m.deleted = b'0'
        AND (${selector});
    `));
    assert.equal(count, 0, `forbidden ${category} menu count must be zero`);
  }
});

test('role SQL fails before mutation when the dashboard home prerequisite is absent', () => {
  const linkSnapshot = queryScalar(isolatedDatabase, `
    SELECT GROUP_CONCAT(CONCAT(role_id, ':', menu_id, ':', deleted) ORDER BY role_id, menu_id, deleted)
    FROM system_role_menu
    WHERE tenant_id = 1;
  `);
  runMysql(isolatedDatabase, "UPDATE system_menu SET deleted = b'1' WHERE component = 'dashboard/home/index';");

  const failedRun = runMysqlFile(isolatedDatabase, 'sql/mysql/hrm_roles_hr_admin.sql', { allowFailure: true });
  assert.notEqual(failedRun.status, 0, 'missing home prerequisite must fail');
  assert.match(`${failedRun.stderr}\n${failedRun.stdout}`, /dashboard|selector|45000/i);

  const afterFailureSnapshot = queryScalar(isolatedDatabase, `
    SELECT GROUP_CONCAT(CONCAT(role_id, ':', menu_id, ':', deleted) ORDER BY role_id, menu_id, deleted)
    FROM system_role_menu
    WHERE tenant_id = 1;
  `);
  assert.equal(afterFailureSnapshot, linkSnapshot, 'failed selector must not mutate role links');
});

test('local API exposes only the process handling path and denies forbidden modules', async () => {
  const deniedEndpoints = [
    ['GET', '/system/menu/list', undefined, 'system menu'],
    ['GET', '/system/user/page?pageNo=1&pageSize=1', undefined, 'system users'],
    ['GET', '/system/role/page?pageNo=1&pageSize=1', undefined, 'system roles'],
    ['GET', '/system/post/page?pageNo=1&pageSize=1', undefined, 'system posts'],
    ['GET', '/system/tenant/page?pageNo=1&pageSize=1', undefined, 'system tenants'],
    ['GET', '/bpm/model/list', undefined, 'process models'],
    ['PUT', '/bpm/model/update-sort-batch?ids=1', undefined, 'process model sort'],
    ['GET', '/bpm/form/page?pageNo=1&pageSize=1', undefined, 'process forms'],
    ['GET', '/bpm/oa/leave/page?pageNo=1&pageSize=1', undefined, 'attendance/leave'],
  ];
  for (const [method, requestPath, body, label] of deniedEndpoints) {
    const result = await apiRequest(method, requestPath, body);
    assert.equal(result.payload.code, 403, `${label} must return logical code 403: ${JSON.stringify(result.payload)}`);
  }
  const organizationEndpoints = [
    ['GET', '/system/dept/list', 'organization list'],
  ];
  for (const [method, requestPath, label] of organizationEndpoints) {
    const result = await apiRequest(method, requestPath);
    assert.equal(result.payload.code, 0, `${label} must remain available: ${JSON.stringify(result.payload)}`);
  }

  const allowedEndpoints = [
    ['GET', '/bpm/process-definition/list?suspensionState=1', 'process definition list'],
    ['GET', '/bpm/process-definition/simple-list', 'process definition picker'],
    ['GET', '/bpm/process-instance/my-page?pageNo=1&pageSize=1', 'my process page'],
    ['GET', '/bpm/task/todo-page?pageNo=1&pageSize=1', 'todo task page'],
    ['GET', '/hrm/employee-archive/page?pageNo=1&pageSize=1', 'HRM employee page'],
  ];
  for (const [method, requestPath, label] of allowedEndpoints) {
    const result = await apiRequest(method, requestPath);
    assert.equal(result.payload.code, 0, `${label} must remain available: ${JSON.stringify(result.payload)}`);
  }

  const permissionInfo = await apiRequest('GET', '/system/auth/get-permission-info');
  assert.equal(permissionInfo.payload.code, 0);
  assert.deepEqual(permissionInfo.payload.data.roles, ['hr_admin']);
});
