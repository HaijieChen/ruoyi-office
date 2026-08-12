/**
 * EXP-82 MySQL migration integration tests for active-only id_card UK (P1-1/2/3).
 *
 * Requires Docker MySQL (ruoyi-office-mysql) and does NOT touch production hrm_employee data:
 * all cases run on isolated tables / a temporary schema clone of DDL only.
 *
 * Usage:
 *   node scripts/local/hrm-employee-id-card-uk-migrate.test.mjs
 *
 * Env (optional):
 *   MYSQL_CONTAINER=ruoyi-office-mysql
 *   MYSQL_ROOT_PASSWORD=123456
 */
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import assert from 'node:assert/strict';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '../..');
const migrateSqlPath = join(
  repoRoot,
  'yudao-module-hrm/yudao-module-hrm-server/src/main/resources/sql/hrm_employee_id_card_uk.sql',
);
const container = process.env.MYSQL_CONTAINER || 'ruoyi-office-mysql';
const password = process.env.MYSQL_ROOT_PASSWORD || '123456';
const schema = `exp82_uk_it_${Date.now()}`;

function mysql(sql, { expectFail = false } = {}) {
  try {
    const out = execFileSync(
      'docker',
      [
        'exec',
        '-i',
        container,
        'mysql',
        '-uroot',
        `-p${password}`,
        '-N',
        '-e',
        sql,
      ],
      { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 },
    );
    if (expectFail) {
      throw new Error(`expected SQL failure, got success:\n${out}`);
    }
    return out.replace(/mysql: \[Warning\].*\n/g, '');
  } catch (e) {
    if (expectFail) {
      return String(e.stderr || e.stdout || e.message);
    }
    throw e;
  }
}

function mysqlFile(filePath, database) {
  // Keep DELIMITER — mysql client stdin supports procedure scripts
  const body = readFileSync(filePath, 'utf8');
  return execFileSync(
    'docker',
    ['exec', '-i', container, 'mysql', '-uroot', `-p${password}`, database],
    { input: body, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 },
  ).replace(/mysql: \[Warning\].*\n/g, '');
}

function setupBaseTable(db) {
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
`);
}

test('migrate: old UK upgrade + lifecycle + blank + active dup', () => {
  setupBaseTable(schema);
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`);
  mysqlFile(migrateSqlPath, schema);

  // lifecycle
  mysql(`USE \`${schema}\`; UPDATE hrm_employee SET deleted=1 WHERE id_card='ID-A' AND deleted=0;`);
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`);
  mysql(`USE \`${schema}\`; UPDATE hrm_employee SET deleted=1 WHERE id_card='ID-A' AND deleted=0;`);
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`);
  // tombstones
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-B', 1, 1), ('ID-B', 1, 1);`);
  // blank cards multiple active allowed (normalized to NULL)
  mysql(`USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('  ', 0, 1), ('', 0, 1);`);
  // active duplicate rejected
  const fail = mysql(
    `USE \`${schema}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-A', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);

  // old UK gone, new UK present
  const idx = mysql(
    `USE \`${schema}\`; SELECT index_name FROM information_schema.statistics WHERE table_schema='${schema}' AND table_name='hrm_employee' AND index_name IN ('uk_hrm_employee_id_card','uk_hrm_employee_active_id_card') GROUP BY index_name;`,
  );
  assert.ok(idx.includes('uk_hrm_employee_active_id_card'));
  assert.ok(!idx.includes('uk_hrm_employee_id_card'));

  // generation has nullif+trim
  const gen = mysql(
    `USE \`${schema}\`; SELECT LOWER(generation_expression) FROM information_schema.columns WHERE table_schema='${schema}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  );
  assert.match(gen, /nullif/);
  assert.match(gen, /trim/);
});

test('migrate: wrong plain column same name is rebuilt (P1-1)', () => {
  const db = `${schema}_badcol`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  active_id_card varchar(18) DEFAULT NULL
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id, active_id_card) VALUES
  ('ID-X', 0, 1, NULL),
  ('ID-X', 0, 1, NULL);
`);
  // precheck fails on active dups
  const fail = (() => {
    try {
      mysqlFile(migrateSqlPath, db);
      return '';
    } catch (e) {
      return String(e.stderr || e.stdout || e.message);
    }
  })();
  assert.match(fail, /precheck failed|45000|Duplicate active/i);

  // clean dups then succeed; wrong column replaced
  mysql(`USE \`${db}\`; DELETE FROM hrm_employee WHERE id > 1;`);
  mysqlFile(migrateSqlPath, db);
  const extra = mysql(
    `SELECT EXTRA FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  );
  assert.match(extra.toUpperCase(), /STORED/);
  // now active dup rejected
  const fail2 = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-X', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail2, /1062|Duplicate/i);
});

test('migrate: wrong non-unique same-name index is rebuilt (P1-1)', () => {
  const db = `${schema}_badidx`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  active_id_card varchar(18) GENERATED ALWAYS AS (IF(deleted = 0, NULLIF(TRIM(id_card), ''), NULL)) STORED,
  KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Y', 0, 1);
`);
  mysqlFile(migrateSqlPath, db);
  const nonUnique = mysql(
    `SELECT non_unique FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_active_id_card' LIMIT 1;`,
  ).trim();
  assert.equal(nonUnique, '0');
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Y', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});

test('migrate: empty-string id_card precheck + expression (P1-2)', () => {
  const db = `${schema}_blank`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1
);
-- two active blanks: precheck must pass (normalized NULL); UK allows both
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('', 0, 1), ('   ', 0, 1);
`);
  mysqlFile(migrateSqlPath, db);
  const cnt = mysql(`USE \`${db}\`; SELECT COUNT(*) FROM hrm_employee WHERE deleted=0;`).trim();
  assert.equal(cnt, '2');
  // real active cards still unique
  mysql(`USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Z', 0, 1);`);
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Z', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});

test('migrate: re-run is idempotent (partial re-run)', () => {
  const db = `${schema}_idem`;
  setupBaseTable(db);
  mysqlFile(migrateSqlPath, db);
  mysqlFile(migrateSqlPath, db); // second run
  const idx = mysql(
    `SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_active_id_card';`,
  ).trim();
  assert.equal(idx, '1');
});

test('migrate: rollback docs — drop new only after asserting we do not reinstall broken old UK', () => {
  // Documents rollback order; validates that after full migrate, DROP old already done
  // and re-adding old (tenant,id_card,deleted) is not performed by script.
  const db = `${schema}_rb`;
  setupBaseTable(db);
  mysqlFile(migrateSqlPath, db);
  const old = mysql(
    `SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_id_card';`,
  ).trim();
  assert.equal(old, '0');
  // schema still protected by new UK
  mysql(`USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-R', 0, 1);`);
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-R', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});


test('migrate: inverted deleted=1 expression must fail closed and keep old UK (P1-1)', () => {
  const db = `${schema}_inv`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  -- 反转：deleted=1 时才有 active 值（错误）
  active_id_card varchar(18) GENERATED ALWAYS AS (IF(deleted = 1, NULLIF(TRIM(id_card), ''), NULL)) STORED,
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted),
  UNIQUE KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-INV', 0, 1);
`);
  let err = '';
  try {
    mysqlFile(migrateSqlPath, db);
  } catch (e) {
    err = String(e.stderr || e.stdout || e.message);
  }
  // 错误表达式必须被探针拒绝；若实现选择安全重建，则最终定义必须正确
  if (err) {
    assert.match(err, /fail-closed|45000|must match|STORED GENERATED/i);
  }
  // 无论失败还是重建成功：最终不得保留 deleted=1 表达式
  const gen = mysql(
    `SELECT LOWER(generation_expression) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  );
  if (gen.trim()) {
    assert.doesNotMatch(gen.replace(/\s+/g, ''), /deleted`?=1/);
    assert.match(gen, /nullif/);
    // 若 migrate 成功修好，旧 UK 可卸；若 fail-closed 中途失败，旧 UK 应仍在
  }
  // 终态：不得保留 deleted=1；若 fail-closed 中途失败则旧 UK 必须仍在
  if (err) {
    assert.match(err, /fail-closed|45000|must match|STORED GENERATED|precheck/i);
    const old = mysql(
      `SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_id_card';`,
    ).trim();
    assert.equal(old, '1', 'fail-closed must not drop old UK while leaving bad definition');
  } else {
    const norm = gen.replace(/\s+/g, '');
    assert.match(norm, /deleted`?=0/);
    assert.doesNotMatch(norm, /deleted`?=1/);
    // 成功重建后 active 同证必须 1062
    const fail = mysql(
      `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-INV', 0, 1);`,
      { expectFail: true },
    );
    assert.match(fail, /1062|Duplicate/i);
  }
});

test('migrate: prefix index active_id_card(1) must rebuild to full column (P1-2)', () => {
  const db = `${schema}_pfx`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  active_id_card varchar(18) GENERATED ALWAYS AS (IF(deleted = 0, NULLIF(TRIM(id_card), ''), NULL)) STORED,
  UNIQUE KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card(1))
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('X1111', 0, 1);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('Y2222', 0, 1);
`);
  // prefix UK wrongly allows different cards with same first char? AB and AC differ at first char so both ok
  // After migrate, full UK must exist with sub_part null
  mysqlFile(migrateSqlPath, db);
  const sub = mysql(
    `SELECT IFNULL(sub_part,'NULL') FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_active_id_card' AND column_name='active_id_card';`,
  ).trim();
  assert.equal(sub, 'NULL');
  const nonUnique = mysql(
    `SELECT non_unique FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_active_id_card' LIMIT 1;`,
  ).trim();
  assert.equal(nonUnique, '0');
  // same full card rejected
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('X1111', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});



test('migrate: NULLIF sentinel ID-Q must not be accepted (P1-1 empty-string only)', () => {
  const db = `${schema}_sentinel`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  -- 错误 sentinel：NULLIF 第二参数为 'ID-Q' 而非空串
  active_id_card varchar(18) GENERATED ALWAYS AS (IF(deleted = 0, NULLIF(TRIM(id_card), 'ID-Q'), NULL)) STORED,
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted),
  UNIQUE KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
-- 当 id_card='ID-Q' 时 active 为 NULL；当 id_card='ID-Q1' 时 active 为 'ID-Q1'
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Q', 0, 1);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-Q', 1, 1);
`);
  // 脚本应判定列错误并安全重建为 '' sentinel
  mysqlFile(migrateSqlPath, db);
  const gen = mysql(
    `SELECT LOWER(REPLACE(generation_expression,' ','')) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  ).trim();
  assert.doesNotMatch(gen, /id-q/i);
  assert.match(gen, /deleted`?=0/);
  assert.match(gen, /nullif\(trim\(/);
  // empty sentinel only — reject non-empty NULLIF 2nd arg like 'ID-Q'
  assert.doesNotMatch(gen, /nullif\(trim\(`id_card`\),(_[a-z0-9]+)?\\'[a-z0-9]/);
  // 双 active 同证必须被新 UK 拒绝
  mysql(`USE \`${db}\`; DELETE FROM hrm_employee; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-S', 0, 1);`);
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-S', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});

test('migrate: utf8mb4_bin collation must rebuild; case-only dup is 1062 (F1)', () => {
  const db = `${schema}_bincol`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  -- 表达式正确但 collation 错误：binary 会放过 case-only 重复
  active_id_card varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
    GENERATED ALWAYS AS (IF(deleted = 0, NULLIF(TRIM(id_card), ''), NULL)) STORED,
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted),
  UNIQUE KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('CASE-ID-X', 0, 1);
`);
  mysqlFile(migrateSqlPath, db);
  const coll = mysql(
    `SELECT LOWER(COLLATION_NAME) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  ).trim();
  assert.equal(coll, 'utf8mb4_unicode_ci');
  const cs = mysql(
    `SELECT LOWER(CHARACTER_SET_NAME) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  ).trim();
  assert.equal(cs, 'utf8mb4');
  // case-only 重复必须 1062（unicode_ci）
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('case-id-x', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
  const old = mysql(
    `SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${db}' AND table_name='hrm_employee' AND index_name='uk_hrm_employee_id_card';`,
  ).trim();
  assert.equal(old, '0');
});

test('migrate: Tab/CR/LF NULLIF sentinel must not be accepted (ws-ctrl)', () => {
  for (const [tag, hex] of [
    ['tab', '09'],
    ['lf', '0A'],
    ['cr', '0D'],
  ]) {
    const db = `${schema}_ws_${tag}`;
    mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
    mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  -- 错误 sentinel：NULLIF 第二参数为控制字符（非精确空串）
  active_id_card varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (IF(deleted = 0, NULLIF(TRIM(id_card), _utf8mb4 0x${hex}), NULL)) STORED,
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted),
  UNIQUE KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-WS-${tag}', 0, 1);
`);
    mysqlFile(migrateSqlPath, db);
    // 用 HEX 检查控制字节是否仍嵌在表达式中（避免客户端换行干扰）
    const genHex = mysql(
      `SELECT HEX(GENERATION_EXPRESSION) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
    ).trim().toUpperCase();
    // 控制字符作为独立字节：在 hex 中为 09/0A/0D，且两侧为转义引号 5C27（\'）
    assert.ok(
      !genHex.includes(`5C27${hex}5C27`),
      `${tag}: control-char NULLIF sentinel must be rebuilt (found \\'0x${hex}\\' in generation)`,
    );
    const gen = mysql(
      `SELECT LOWER(REPLACE(GENERATION_EXPRESSION,' ','')) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
    ).trim();
    assert.match(gen, /nullif\(trim\(/);
    assert.match(gen, /deleted`?=0/);
    // 权威空串：多条空白 active 允许（映射为 NULL）
    mysql(`USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('', 0, 1), ('  ', 0, 1);`);
    const blanks = mysql(
      `USE \`${db}\`; SELECT COUNT(*) FROM hrm_employee WHERE deleted=0 AND NULLIF(TRIM(id_card),'') IS NULL;`,
    ).trim();
    assert.equal(blanks, '2', `${tag}: blank actives must be allowed after empty-string sentinel rebuild`);
    // 同证 active 仍唯一
    const fail = mysql(
      `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-WS-${tag}', 0, 1);`,
      { expectFail: true },
    );
    assert.match(fail, /1062|Duplicate/i);
  }
});

test('migrate: single-space NULLIF sentinel must rebuild; blanks multi-insert OK (F1)', () => {
  const db = `${schema}_spcsent`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  -- 错误：单 ASCII 空格 sentinel（全局去空格会被误判为 ''）
  active_id_card varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (IF(deleted = 0, NULLIF(TRIM(id_card), ' '), NULL)) STORED,
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted),
  UNIQUE KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-SP', 0, 1);
`);
  mysqlFile(migrateSqlPath, db);
  // 终态 generation 不得再把 ' ' 当作 sentinel：hex 中不应出现 5C27205C27（\' \')
  const genHex = mysql(
    `SELECT HEX(GENERATION_EXPRESSION) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  ).trim().toUpperCase();
  assert.ok(!genHex.includes('5C27205C27'), 'space sentinel must be rebuilt to empty string');
  // 多条空白 active 必须可插入（权威 NULLIF(...,'')）
  mysql(`USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('', 0, 1), ('  ', 0, 1);`);
  const blanks = mysql(
    `USE \`${db}\`; SELECT COUNT(*) FROM hrm_employee WHERE deleted=0 AND NULLIF(TRIM(id_card),'') IS NULL;`,
  ).trim();
  assert.equal(blanks, '2');
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-SP', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
});

test('migrate: nested compound NULLIF sentinel must rebuild; dual active 1062 (F2)', () => {
  const db = `${schema}_nest`;
  mysql(`CREATE DATABASE IF NOT EXISTS \`${db}\``);
  mysql(`
USE \`${db}\`;
DROP TABLE IF EXISTS hrm_employee;
CREATE TABLE hrm_employee (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_card varchar(18) DEFAULT NULL,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 1,
  -- 审查复现：嵌套复合第二参数会让 -1 后缀探针吃到内层空串
  active_id_card varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (
      IF(deleted = 0, NULLIF(TRIM(id_card), IF(1 = 1, NULLIF(TRIM(id_card), ''), NULL)), NULL)
    ) STORED,
  UNIQUE KEY uk_hrm_employee_id_card (tenant_id, id_card, deleted),
  UNIQUE KEY uk_hrm_employee_active_id_card (tenant_id, active_id_card)
);
INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-NEST', 0, 1);
`);
  // 错误定义下同证双 active 可能被放行；迁移后必须 1062
  mysqlFile(migrateSqlPath, db);
  const gen = mysql(
    `SELECT LOWER(GENERATION_EXPRESSION) FROM information_schema.columns WHERE table_schema='${db}' AND table_name='hrm_employee' AND column_name='active_id_card';`,
  ).trim();
  // 终态仅一层 nullif(trim
  const cnt = (gen.match(/nullif\s*\(\s*trim\s*\(/g) || []).length;
  assert.equal(cnt, 1, 'nested nullif must be rebuilt to single outer nullif');
  assert.doesNotMatch(gen.replace(/\s+/g, ''), /nullif\(trim\(`?id_card`?\),if\(/);
  const fail = mysql(
    `USE \`${db}\`; INSERT INTO hrm_employee(id_card, deleted, tenant_id) VALUES ('ID-NEST', 0, 1);`,
    { expectFail: true },
  );
  assert.match(fail, /1062|Duplicate/i);
  const active = mysql(
    `USE \`${db}\`; SELECT COUNT(*) FROM hrm_employee WHERE deleted=0 AND id_card='ID-NEST';`,
  ).trim();
  assert.equal(active, '1');
});


test.after(() => {
  try {
    mysql(`DROP DATABASE IF EXISTS \`${schema}\``);
    for (const s of [
      `${schema}_badcol`,
      `${schema}_badidx`,
      `${schema}_blank`,
      `${schema}_idem`,
      `${schema}_rb`,
      `${schema}_inv`,
      `${schema}_pfx`,
      `${schema}_sentinel`,
      `${schema}_bincol`,
      `${schema}_ws_tab`,
      `${schema}_ws_lf`,
      `${schema}_ws_cr`,
      `${schema}_spcsent`,
      `${schema}_nest`,
    ]) {
      mysql(`DROP DATABASE IF EXISTS \`${s}\``);
    }
  } catch {
    // ignore cleanup errors
  }
});
